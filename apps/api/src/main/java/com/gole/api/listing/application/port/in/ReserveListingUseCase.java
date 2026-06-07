package com.gole.api.listing.application.port.in;

import com.gole.api.listing.domain.model.Listing;
import java.util.Optional;

/**
 * Inbound port: 주문을 위한 원자적 리스팅 선점. (요구사항 13.1)
 */
public interface ReserveListingUseCase {

    Optional<Listing> reserve(String listingId);
}
