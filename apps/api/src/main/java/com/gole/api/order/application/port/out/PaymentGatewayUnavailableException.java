package com.gole.api.order.application.port.out;

/**
 * 결제 승인 여부를 확정할 수 없는 일시적 PG 장애.
 *
 * <p>결제 거절({@code false})과 구분해 주문을 PAYMENT_PENDING으로 보존하고 웹훅 재시도를 허용한다.
 */
public class PaymentGatewayUnavailableException extends RuntimeException {

    public PaymentGatewayUnavailableException(String orderId, Throwable cause) {
        super("Payment verification is temporarily unavailable for order " + orderId, cause);
    }
}
