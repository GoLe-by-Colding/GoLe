package com.gole.api.order.application.port.in;

/**
 * Inbound port: 주문 생성(리스팅 원자적 선점 → 결제 대기). (요구사항 13.1)
 */
public interface PlaceOrderUseCase {

    String place(PlaceOrderCommand command);

    /**
     * @param buyerPhone 구매자 CS 연락처(R8.1). 미수집 호출 경로(레거시)를 위해 null 허용.
     */
    record PlaceOrderCommand(String listingId, String buyerId, String buyerPhone) {

        public PlaceOrderCommand(String listingId, String buyerId) {
            this(listingId, buyerId, null);
        }
    }
}
