package com.gole.api.order.adapter.out.settlement;

import com.gole.api.common.operations.OperationalEvent;
import com.gole.api.common.operations.OperationalEvent.Category;
import com.gole.api.common.operations.OperationalEvent.Level;
import com.gole.api.common.operations.OperationalEventPublisher;
import com.gole.api.launch.application.port.in.GetLaunchConfigUseCase;
import com.gole.api.launch.domain.model.LaunchFeature;
import com.gole.api.order.application.port.out.AutomaticSettlementPort;
import com.gole.api.order.application.port.out.AutomaticSettlementPort.Candidate;
import com.gole.api.order.application.port.out.OrderRepositoryPort;
import com.gole.api.order.domain.model.OrderStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 지급 유예가 끝난 정산을 외부 지급대행으로 보낸다.
 *
 * <p>원장을 먼저 원자 선점한 뒤 외부 호출을 하고, 주문 ID를 외부 멱등키로 사용한다. 프로세스가
 * 외부 지급 직후 죽더라도 stale 선점을 다시 가져와 같은 주문 ID로 호출하므로 이중 지급되지
 * 않는다. 외부 구현체도 이 멱등 계약을 반드시 지켜야 한다.
 */
@Component
public class ProviderSettlementScheduler {

    private static final Logger log = LoggerFactory.getLogger(ProviderSettlementScheduler.class);

    private final AutomaticSettlementPort settlements;
    private final OrderRepositoryPort orders;
    private final SettlementExecutor executor;
    private final SettlementProperties properties;
    private final GetLaunchConfigUseCase launchConfig;
    private final OperationalEventPublisher operationalEvents;
    private final Clock clock;

    public ProviderSettlementScheduler(
            AutomaticSettlementPort settlements,
            OrderRepositoryPort orders,
            SettlementExecutor executor,
            SettlementProperties properties,
            GetLaunchConfigUseCase launchConfig,
            OperationalEventPublisher operationalEvents,
            Clock clock) {
        this.settlements = settlements;
        this.orders = orders;
        this.executor = executor;
        this.properties = properties;
        this.launchConfig = launchConfig;
        this.operationalEvents = operationalEvents;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${gole.settlement.provider-scan-interval:PT1M}",
            initialDelayString = "${gole.settlement.provider-initial-delay:PT30S}")
    public void scan() {
        processDue();
    }

    /** 테스트와 운영 수동 재조정에서 한 주기를 명시적으로 실행할 수 있다. */
    public int processDue() {
        // 어드민의 긴급 정지 override가 실제 돈의 이동을 멈추는 권위 게이트다.
        // 설정 조회가 실패해 예외가 나도 아래 선점/지급 경로에는 도달하지 않아 fail-closed다.
        if (!launchConfig.isEnabled(LaunchFeature.PARTNER_PAYOUT) || !executor.canPayAutomatically()) {
            return 0;
        }
        settlements.blockExhaustedClaims(
                Instant.now(clock), properties.getProviderClaimTimeout(), properties.getProviderMaxAttempts());
        int processed = 0;
        for (int i = 0; i < properties.getProviderBatchSize(); i++) {
            Instant now = Instant.now(clock);
            String attemptId = UUID.randomUUID().toString();
            Optional<Candidate> claimed = settlements.claimNext(
                    now, properties.getPayoutHoldback(), properties.getProviderClaimTimeout(), attemptId);
            if (claimed.isEmpty()) {
                break;
            }
            process(claimed.orElseThrow(), now);
            processed++;
        }
        return processed;
    }

    private void process(Candidate candidate, Instant now) {
        var order = orders.findById(candidate.orderId()).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.COMPLETED) {
            String status = order == null ? "missing" : order.getStatus().name();
            try {
                settlements.markBlocked(
                        candidate.orderId(), candidate.attemptId(), "권위 주문이 지급 가능 상태가 아님: " + status, now);
            } catch (RuntimeException recordFailure) {
                log.error(
                        "지급대행 차단 상태 기록 실패 orderId={} attempt={}",
                        candidate.orderId(),
                        candidate.attemptId(),
                        recordFailure);
            }
            publishFailure("판매자 자동 지급 차단", "권위 주문과 정산 원장이 일치하지 않아 지급을 차단했습니다.", candidate, status, now);
            log.error("지급대행 차단 orderId={} orderStatus={}", candidate.orderId(), status);
            return;
        }

        String reference;
        try {
            reference = executor.payIfAutomatic(candidate.orderId(), candidate.sellerId(), candidate.payout())
                    .filter(value -> !value.isBlank())
                    .orElseThrow(() -> new IllegalStateException("지급대행 모드가 실행 중 비활성화됐거나 증빙 번호가 비어 있습니다"));
        } catch (RuntimeException failure) {
            Instant failedAt = Instant.now(clock);
            boolean exhausted = candidate.attemptNumber() >= properties.getProviderMaxAttempts();
            try {
                if (exhausted) {
                    settlements.markBlocked(
                            candidate.orderId(),
                            candidate.attemptId(),
                            "지급대행 재시도 상한 도달 (%d/%d): %s"
                                    .formatted(
                                            candidate.attemptNumber(),
                                            properties.getProviderMaxAttempts(),
                                            failure.getMessage()),
                            failedAt);
                } else {
                    settlements.markFailed(
                            candidate.orderId(),
                            candidate.attemptId(),
                            failure.getMessage(),
                            failedAt,
                            properties.getProviderRetryAfter());
                }
            } catch (RuntimeException recordFailure) {
                log.error(
                        "지급대행 실패 상태 기록 실패 orderId={} attempt={}",
                        candidate.orderId(),
                        candidate.attemptId(),
                        recordFailure);
            }
            publishFailure(
                    exhausted ? "판매자 자동 지급 재시도 중단" : "판매자 자동 지급 실패",
                    exhausted
                            ? "외부 지급대행 재시도 상한에 도달해 원장을 차단했습니다. 관리자가 원인과 지급 결과를 확인해야 합니다."
                            : "외부 지급대행 호출에 실패해 재시도 대상으로 보존했습니다.",
                    candidate,
                    "%s · %d/%d"
                            .formatted(
                                    failure.getClass().getSimpleName(),
                                    candidate.attemptNumber(),
                                    properties.getProviderMaxAttempts()),
                    failedAt);
            log.error("지급대행 실행 실패 orderId={} attempt={}", candidate.orderId(), candidate.attemptId(), failure);
            return;
        }

        try {
            settlements.markPaid(candidate.orderId(), candidate.attemptId(), reference, Instant.now(clock));
            log.info("지급대행 원장 반영 완료 orderId={} attempt={}", candidate.orderId(), candidate.attemptId());
        } catch (RuntimeException recordFailure) {
            // 외부 지급은 이미 성공했다. 실패 상태로 되돌리면 새 지급처럼 보이므로 선점을
            // 유지하고, stale 재선점 때 같은 주문 ID로 외부 멱등 조회/호출하게 한다.
            Instant failedAt = Instant.now(clock);
            publishFailure(
                    "자동 지급 성공 후 원장 반영 실패",
                    "외부 지급은 성공했지만 내부 증빙 기록에 실패했습니다. 주문 ID 멱등키로 재조정이 필요합니다.",
                    candidate,
                    recordFailure.getClass().getSimpleName(),
                    failedAt);
            log.error(
                    "지급대행 성공 후 원장 반영 실패 orderId={} attempt={} reference={}",
                    candidate.orderId(),
                    candidate.attemptId(),
                    reference,
                    recordFailure);
        }
    }

    private void publishFailure(
            String title, String description, Candidate candidate, String detail, Instant occurredAt) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("주문 ID", candidate.orderId());
        fields.put("시도 ID", candidate.attemptId());
        fields.put("상세", detail);
        operationalEvents.publish(
                new OperationalEvent(Category.PAYMENT, Level.ERROR, title, description, fields, occurredAt));
    }
}
