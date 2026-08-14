package com.gole.api.order.adapter.out.payment;

import com.gole.api.order.application.port.out.PaymentGatewayPort;
import com.gole.api.order.domain.model.PaymentMethod;
import com.gole.api.order.domain.model.PaymentMethodType;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 결제 게이트웨이 스텁 어댑터. 결정론적으로 성공 결과를 반환한다.
 *
 * <p>{@code portone.enabled=true} 이면 {@link PortOnePaymentGatewayAdapter}가 대신 사용된다(기본은 스텁).
 */
@Component
@ConditionalOnProperty(name = "portone.enabled", havingValue = "false", matchIfMissing = true)
public class StubPaymentGatewayAdapter implements PaymentGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(StubPaymentGatewayAdapter.class);

    @Override
    public PaymentAuthorization authorize(String orderId, long amount) {
        String transactionId = newTransactionId(orderId);
        // TODO: integrate real PG (Toss/PortOne)
        log.info("[STUB-PG] authorize success orderId={} amount={} transactionId={}", orderId, amount, transactionId);
        // 스텁은 카드 결제를 가정한다. 실제 수단은 PG만 알 수 있으므로 여기서 지어내지 않는다.
        return PaymentAuthorization.approved(PaymentMethod.of(PaymentMethodType.CARD));
    }

    @Override
    public void refund(String orderId, long amount) {
        String transactionId = newTransactionId(orderId);
        // TODO: integrate real PG (Toss/PortOne)
        log.info("[STUB-PG] refund success orderId={} amount={} transactionId={}", orderId, amount, transactionId);
    }

    /** 주문 식별자 기반의 결정론적 거래 식별자. */
    private String newTransactionId(String orderId) {
        return "STUB-" + UUID.nameUUIDFromBytes(orderId.getBytes());
    }
}
