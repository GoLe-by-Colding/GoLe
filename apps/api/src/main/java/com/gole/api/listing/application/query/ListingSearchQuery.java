package com.gole.api.listing.application.query;

import com.gole.api.listing.domain.model.ItemCondition;
import com.gole.api.listing.domain.model.ListingCategory;

/**
 * 리스팅 검색 조건. null 필드는 해당 필터를 적용하지 않는다. (요구사항 14)
 * 검색은 항상 활성(ACTIVE) 리스팅만 대상으로 한다.
 */
public record ListingSearchQuery(
        String text,
        ItemCondition condition,
        Long minPrice,
        Long maxPrice,
        ListingSortOrder sort,
        ListingCategory category,
        String setNumber) {

    public ListingSearchQuery {
        if (sort == null) {
            sort = ListingSortOrder.NEWEST;
        }
        if (setNumber != null && setNumber.isBlank()) {
            setNumber = null;
        }
    }

    /** 세트번호 필터 없는 검색(레거시 호환). */
    public ListingSearchQuery(
            String text,
            ItemCondition condition,
            Long minPrice,
            Long maxPrice,
            ListingSortOrder sort,
            ListingCategory category) {
        this(text, condition, minPrice, maxPrice, sort, category, null);
    }

    /** 카테고리 필터 없는 검색(레거시 호환). */
    public ListingSearchQuery(
            String text, ItemCondition condition, Long minPrice, Long maxPrice, ListingSortOrder sort) {
        this(text, condition, minPrice, maxPrice, sort, null, null);
    }

    public static ListingSearchQuery newestAll() {
        return new ListingSearchQuery(null, null, null, null, ListingSortOrder.NEWEST, null, null);
    }

    /** 특정 카탈로그 세트의 활성 매물(최신순). 세트 상세 페이지용. (SEO R1.3) */
    public static ListingSearchQuery forSet(String setNumber) {
        return new ListingSearchQuery(null, null, null, null, ListingSortOrder.NEWEST, null, setNumber);
    }
}
