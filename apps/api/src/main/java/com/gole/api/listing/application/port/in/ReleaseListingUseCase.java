package com.gole.api.listing.application.port.in;

/**
 * Inbound port: 선점 해제(RESERVED → ACTIVE). 결제 실패/환불 시.
 */
public interface ReleaseListingUseCase {

    void release(String listingId);
}
