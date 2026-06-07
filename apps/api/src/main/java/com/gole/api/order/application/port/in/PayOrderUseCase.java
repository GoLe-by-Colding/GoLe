package com.gole.api.order.application.port.in;

import com.gole.api.order.domain.model.OrderStatus;

/**
 * Inbound port: 주문 결제. 성공 시 funds-held, 실패 시 payment-failed. (요구사항 13.2, 13.3)
 */
public interface PayOrderUseCase {

    OrderStatus pay(String orderId);
}
