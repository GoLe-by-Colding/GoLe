package com.gole.api.order.adapter.out.settlement;

import com.gole.api.order.application.port.out.SettlementPort;
import com.gole.api.order.domain.model.FeePolicy;
import com.gole.api.order.domain.model.Settlement;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 정산 어댑터. 도메인 {@link Settlement#compute}로 정산 전표를 결정론적으로 계산한다.
 * (요구사항 13.4, 13.5)
 *
 * <p><b>전표는 실제다</b> — 반환된 {@link Settlement}는 호출자가 주문에 붙여 영속화하므로
 * 수수료·정산액이 조회·정산 대사(對査)에 그대로 쓰인다. 스텁으로 남은 것은 <b>지급 실행</b>뿐이다.
 *
 * <p>TODO: 실제 지급 실행(PG 정산 API 연동). 판매자에게 대금이 실제로 이체되는 단계는 아직 없다.
 * 플랫폼이 자금을 직접 보관·송금하는 구조는 전자금융 관련 등록 문제가 따르므로,
 * PG의 하위 판매자 정산 대행을 붙이는 방향이 먼저 검토되어야 한다.
 */
@Component
public class StubSettlementAdapter implements SettlementPort {

    private static final Logger log = LoggerFactory.getLogger(StubSettlementAdapter.class);

    private final Clock clock;
    private final FeePolicy feePolicy;

    public StubSettlementAdapter(Clock clock, FeePolicy feePolicy) {
        this.clock = clock;
        this.feePolicy = feePolicy;
    }

    @Override
    public Settlement settleOnce(String orderId, String sellerId, long amount) {
        Settlement settlement = Settlement.compute(orderId, sellerId, amount, feePolicy, Instant.now(clock));
        // TODO: 실제 지급 실행. 아래 로그는 전표 계산 결과이지 이체 완료가 아니다.
        log.info(
                "[SETTLEMENT] computed orderId={} sellerId={} gross={} fee={} rate={} payout={} (지급 실행은 미연동)",
                settlement.orderId(),
                settlement.sellerId(),
                settlement.grossAmount(),
                settlement.fee(),
                settlement.feeRate(),
                settlement.payout());
        return settlement;
    }
}
