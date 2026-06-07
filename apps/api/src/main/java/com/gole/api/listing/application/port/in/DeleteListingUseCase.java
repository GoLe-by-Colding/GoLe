package com.gole.api.listing.application.port.in;

/**
 * Inbound port: 리스팅 삭제. (요구사항 5.7, 5.8)
 */
public interface DeleteListingUseCase {

    void delete(String listingId);
}
