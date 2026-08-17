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

    /**
     * 운영에서 허용하는 것과 <b>같은</b> 결제수단을 보고한다. 스텁이 결제수단을 비워두면
     * 개발·E2E에서는 늘 "정보 없음"만 보이고, 실제 PortOne을 붙이는 순간 처음 보는 화면이 된다.
     */
    private static final PaymentMethod STUB_PAYMENT_METHOD = new PaymentMethod(PaymentMethodType.EASY_PAY, "KAKAOPAY");

    @Override
    public PaymentVerification verifyPayment(String orderId, long amount) {
        String transactionId = newTransactionId(orderId);
        // TODO: integrate real PG (Toss/PortOne)
        log.info("[STUB-PG] authorize success orderId={} amount={} transactionId={}", orderId, amount, transactionId);
        return PaymentVerification.paid(STUB_PAYMENT_METHOD);
    }

    @Override
    public RefundResult refund(String orderId, long amount) {
        String transactionId = newTransactionId(orderId);
        log.info("[STUB-PG] refund success orderId={} amount={} transactionId={}", orderId, amount, transactionId);
        return RefundResult.SUCCEEDED;
    }

    @Override
    public boolean isFullyRefunded(String orderId, long amount) {
        return true;
    }

    /** 주문 식별자 기반의 결정론적 거래 식별자. */
    private String newTransactionId(String orderId) {
        return "STUB-" + UUID.nameUUIDFromBytes(orderId.getBytes());
    }
}
