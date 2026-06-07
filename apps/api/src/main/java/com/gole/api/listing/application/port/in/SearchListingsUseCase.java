package com.gole.api.listing.application.port.in;

import com.gole.api.listing.application.query.ListingSearchQuery;
import com.gole.api.listing.domain.model.Listing;
import java.util.List;

/**
 * Inbound port: 활성 리스팅 검색/필터/정렬. (요구사항 14)
 */
public interface SearchListingsUseCase {

    List<Listing> search(ListingSearchQuery query);
}
