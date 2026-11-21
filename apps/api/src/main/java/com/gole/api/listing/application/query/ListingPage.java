package com.gole.api.listing.application.query;

import com.gole.api.listing.domain.model.Listing;
import java.util.List;
import java.util.Optional;

/** 매물 검색 한 페이지. 다음 페이지가 없으면 {@code next}가 비어 있다. (listing-search-paging P1·P4) */
public record ListingPage(List<Listing> items, Optional<ListingPageCursor> next) {

    public static final int DEFAULT_LIMIT = 24;
    public static final int MAX_LIMIT = 60;

    public ListingPage {
        items = List.copyOf(items);
    }

    /** 요청 크기를 1~{@value #MAX_LIMIT}로 당긴다(P2). */
    public static int boundedLimit(int requested) {
        return Math.clamp(requested, 1, MAX_LIMIT);
    }
}
