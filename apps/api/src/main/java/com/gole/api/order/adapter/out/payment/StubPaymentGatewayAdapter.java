package com.gole.api.order.adapter.out.payment;

import com.gole.api.order.application.port.out.PaymentGatewayPort;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 결제 게이트웨이 스텁 어댑터. 결정론적으로 성공 결과를 반환한다.
 *
 * <p>TODO: integrate real PG (Toss/PortOne)
 */
@Component
public class StubPaymentGatewayAdapter implements PaymentGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(StubPaymentGatewayAdapter.class);

    @Override
    public boolean authorize(String orderId, long amount) {
        String transactionId = newTransactionId(orderId);
        // TODO: integrate real PG (Toss/PortOne)
        log.info(
                "[STUB-PG] authorize success orderId={} amount={} transactionId={}",
                orderId,
                amount,
                transactionId);
        return true;
    }

    @Override
    public void refund(String orderId, long amount) {
        String transactionId = newTransactionId(orderId);
        // TODO: integrate real PG (Toss/PortOne)
        log.info(
                "[STUB-PG] refund success orderId={} amount={} transactionId={}",
                orderId,
                amount,
                transactionId);
    }

    /** 주문 식별자 기반의 결정론적 거래 식별자. */
    private String newTransactionId(String orderId) {
        return "STUB-" + UUID.nameUUIDFromBytes(orderId.getBytes());
    }
}
