package com.gole.api.order.application.port.in;

import com.gole.api.order.domain.model.OrderStatus;

/**
 * Inbound port: 주문 결제. 성공 시 funds-held, 실패 시 payment-failed. (요구사항 13.2, 13.3)
 */
public interface PayOrderUseCase {

    OrderStatus pay(String orderId);

    /**
     * PG 결제 식별자로 결제를 반영한다. 웹훅 전용 진입점이다.
     *
     * <p>웹훅은 주문 id를 모르고 결제 식별자만 들고 온다. 결제 식별자는 시도마다 달라지므로
     * 주문 id와 같지 않다 — 둘을 같은 것으로 취급하면 재시도한 주문의 웹훅이 전부 버려진다.
     */
    OrderStatus payByPaymentId(String paymentId);
}
