package com.gole.api.listing.application.port.in;

/**
 * Inbound port: 판매 완료 처리. (요구사항 5.6)
 */
public interface MarkListingSoldUseCase {

    /** 주문 구매확정 경로. 진행 중 주문(RESERVED)의 매물도 판매 완료로 바꾼다. */
    void markSold(String listingId);

    /**
     * 판매자가 직접 판매 완료로 바꾼다. 판매 중(ACTIVE)일 때만 바꾸고, 진행 중 주문이 있으면 거절한다 —
     * 결제한 구매자의 주문이 남은 채 매물만 SOLD가 되면 환불해도 매물이 판매 중으로 돌아오지 않는다.
     * 이미 판매 완료면 그대로 둔다.
     */
    void markSoldBySeller(String listingId);

    /** 직거래 전용. ACTIVE 매물만 한 번 SOLD로 선점하며 주문 예약과 경쟁하면 한쪽만 성공한다. */
    boolean markDirectTradeSoldIfActive(String listingId);
}
