package com.gole.api.order.adapter.out.settlement;

import com.gole.api.order.application.port.out.SettlementPort;
import com.gole.api.order.domain.model.Settlement;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 정산 스텁 어댑터. 도메인 {@link Settlement#compute}로 결정론적으로 정산을 계산/기록한다.
 * (요구사항 13.4, 13.5)
 *
 * <p>TODO: real settlement
 */
@Component
public class StubSettlementAdapter implements SettlementPort {

    private static final Logger log = LoggerFactory.getLogger(StubSettlementAdapter.class);

    private final Clock clock;

    public StubSettlementAdapter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void settleOnce(String orderId, String sellerId, long amount) {
        Settlement settlement = Settlement.compute(orderId, sellerId, amount, Instant.now(clock));
        // TODO: real settlement
        log.info(
                "[STUB-SETTLEMENT] settled orderId={} sellerId={} gross={} fee={} payout={}",
                settlement.orderId(),
                settlement.sellerId(),
                settlement.grossAmount(),
                settlement.fee(),
                settlement.payout());
    }
}
