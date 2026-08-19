package com.gole.api.order.application.service.pipeline;

import com.gole.api.common.operations.OperationalEvent;
import com.gole.api.common.operations.OperationalEvent.Category;
import com.gole.api.common.operations.OperationalEvent.Level;
import com.gole.api.common.operations.OperationalEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 주문 파이프라인 스케줄러. (shipping-and-fees R7 — 무개입 원칙의 엔진)
 *
 * <p>상태별 타임아웃 규칙({@link PipelineRule})을 하나의 루프에서 순회한다 — 규칙을 개별
 * 스케줄러로 흩뿌리면 정책이 코드 곳곳에 숨는다(설계 판단). 건별 실패는 격리되어
 * 한 주문의 오류가 배치를 멈추지 않는다(R7.4).
 */
@Component
public class OrderPipelineScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderPipelineScheduler.class);

    private final List<PipelineRule> rules;
    private final OperationalEventPublisher operationalEvents;
    private final Clock clock;

    public OrderPipelineScheduler(List<PipelineRule> rules, OperationalEventPublisher operationalEvents, Clock clock) {
        this.rules = rules;
        this.operationalEvents = operationalEvents;
        this.clock = clock;
    }

    @Scheduled(
            initialDelayString = "${gole.pipeline.initial-delay:PT30S}",
            fixedDelayString = "${gole.pipeline.interval:PT1M}")
    public void run() {
        runOnce(Instant.now(clock));
    }

    /** 테스트에서 시각을 고정해 호출할 수 있도록 분리한다. */
    public void runOnce(Instant now) {
        Map<String, Integer> acted = new LinkedHashMap<>();
        int failed = 0;
        for (PipelineRule rule : rules) {
            List<String> candidates;
            try {
                candidates = rule.candidates(now);
            } catch (RuntimeException e) {
                failed++;
                log.warn("[pipeline] rule={} candidate query failed: {}", rule.name(), e.getMessage());
                continue;
            }
            for (String orderId : candidates) {
                try {
                    if (rule.apply(orderId, now)) {
                        acted.merge(rule.name(), 1, Integer::sum);
                    }
                } catch (RuntimeException e) {
                    failed++;
                    log.warn("[pipeline] rule={} orderId={} failed: {}", rule.name(), orderId, e.getMessage());
                }
            }
        }
        if (!acted.isEmpty() || failed > 0) {
            Map<String, String> fields = new LinkedHashMap<>();
            acted.forEach((rule, count) -> fields.put(rule, Integer.toString(count)));
            if (failed > 0) {
                fields.put("실패", Integer.toString(failed));
            }
            operationalEvents.publish(new OperationalEvent(
                    Category.APPLICATION,
                    failed > 0 ? Level.WARNING : Level.INFO,
                    "주문 파이프라인 자동 처리",
                    "상태별 타임아웃 규칙이 자동 처리했습니다. 정상 거래에는 운영자 개입이 없습니다.",
                    fields,
                    now));
        }
    }
}
