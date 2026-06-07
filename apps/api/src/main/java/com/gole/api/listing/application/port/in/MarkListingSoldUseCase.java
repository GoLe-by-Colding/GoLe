package com.gole.api.listing.application.port.in;

/**
 * Inbound port: 판매 완료 처리. (요구사항 5.6)
 */
public interface MarkListingSoldUseCase {

    void markSold(String listingId);
}
