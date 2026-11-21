package com.gole.api.listing.application.port.in;

import com.gole.api.listing.application.query.ListingPage;
import com.gole.api.listing.application.query.ListingPageCursor;
import com.gole.api.listing.application.query.ListingSearchQuery;
import java.util.Optional;

/** Inbound port: 활성 매물 검색을 페이지로 나눠 읽는다. (listing-search-paging P1~P4) */
public interface SearchListingPagesUseCase {

    /** {@code after}가 비면 첫 페이지다. {@code limit}은 1~60으로 당긴다. */
    ListingPage searchPage(ListingSearchQuery query, Optional<ListingPageCursor> after, int limit);
}
