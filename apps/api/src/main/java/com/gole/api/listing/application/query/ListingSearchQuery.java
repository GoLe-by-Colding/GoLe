package com.gole.api.listing.application.query;

import com.gole.api.listing.domain.model.ItemCondition;

/**
 * 리스팅 검색 조건. null 필드는 해당 필터를 적용하지 않는다. (요구사항 14)
 * 검색은 항상 활성(ACTIVE) 리스팅만 대상으로 한다.
 */
public record ListingSearchQuery(
        String text,
        ItemCondition condition,
        Long minPrice,
        Long maxPrice,
        ListingSortOrder sort) {

    public ListingSearchQuery {
        if (sort == null) {
            sort = ListingSortOrder.NEWEST;
        }
    }

    public static ListingSearchQuery newestAll() {
        return new ListingSearchQuery(null, null, null, null, ListingSortOrder.NEWEST);
    }
}
