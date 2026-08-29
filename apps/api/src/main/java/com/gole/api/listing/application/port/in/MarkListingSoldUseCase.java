package com.gole.api.listing.application.port.in;

/**
 * Inbound port: 판매 완료 처리. (요구사항 5.6)
 */
public interface MarkListingSoldUseCase {

    void markSold(String listingId);

    /** 직거래 전용. ACTIVE 매물만 한 번 SOLD로 선점하며 주문 예약과 경쟁하면 한쪽만 성공한다. */
    boolean markDirectTradeSoldIfActive(String listingId);
}
