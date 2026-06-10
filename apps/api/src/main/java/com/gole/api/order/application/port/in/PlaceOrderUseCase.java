package com.gole.api.order.application.port.in;

/**
 * Inbound port: 주문 생성(리스팅 원자적 선점 → 결제 대기). (요구사항 13.1)
 */
public interface PlaceOrderUseCase {

    String place(PlaceOrderCommand command);

    record PlaceOrderCommand(String listingId, String buyerId) {}
}
