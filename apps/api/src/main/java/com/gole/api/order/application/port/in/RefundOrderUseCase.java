package com.gole.api.order.application.port.in;

/**
 * Inbound port: 환불(funds-held → refunded, 리스팅 복구). (요구사항 13.6)
 */
public interface RefundOrderUseCase {

    void refund(String orderId);
}
