package com.gole.api.listing.application.query;

import com.gole.api.listing.domain.model.InterestTag;
import com.gole.api.listing.domain.model.ItemCondition;
import com.gole.api.listing.domain.model.ListingCategory;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /**
     * 검색어가 세트 번호처럼 생겼을 때의 기본 번호 — "75192", "#75192", 브릭링크식 변형 번호 "10307-1"은 각각 "75192",
     * "75192", "10307". 세트 번호로 보기 어려운 검색어면 {@code null}.
     *
     * <p>판매자는 세트 번호를 전용 칸(`catalogSetNumber`)에 넣고 제목에는 세트 이름만 쓰는 일이 많다. 제목·설명 글자만 보면
     * 번호로 찾는 구매자가 그 매물을 놓친다. 변형 번호 접미사(-1)는 같은 세트를 가리키므로 떼고 찾는다.
     */
    public String setNumberInText() {
        if (text == null) {
            return null;
        }
        Matcher matcher = SET_NUMBER_TEXT.matcher(text.trim());
        return matcher.matches() ? matcher.group(1) : null;
    }

    /**
     * 검색어가 관심 테마 이름이면 그 테마 — "스타워즈"·"스타 워즈"·"Star Wars"·"star-wars"는 모두 {@link InterestTag#STAR_WARS}.
     * 아니면 {@code null}. 대소문자·띄어쓰기·하이픈은 가리지 않고, 이름의 일부("스타")는 테마로 보지 않는다.
     *
     * <p>카탈로그 테마 이름은 영어("Star Wars")이고 한국어 매물 제목에는 테마 이름이 거의 없어, "스타워즈"로 찾으면 0건이었다.
     * 판매자가 고른 관심 테마로도 찾게 한다.
     */
    public InterestTag interestTagInText() {
        if (text == null) {
            return null;
        }
        String needle = normalizeTagText(text);
        if (needle.isEmpty()) {
            return null;
        }
        for (InterestTag tag : InterestTag.values()) {
            if (needle.equals(normalizeTagText(tag.label())) || needle.equals(normalizeTagText(tag.key()))) {
                return tag;
            }
        }
        return null;
    }

    private static String normalizeTagText(String value) {
        return value.replaceAll("[\\s_-]+", "").toLowerCase(Locale.ROOT);
    }

    private static final Pattern SET_NUMBER_TEXT = Pattern.compile("#?(\\d{3,7})(?:-\\d{1,2})?");

    public static ListingSearchQuery newestAll() {
        return new ListingSearchQuery(null, null, null, null, ListingSortOrder.NEWEST, null, null);
    }

    /** 특정 카탈로그 세트의 활성 매물(최신순). 세트 상세 페이지용. (SEO R1.3) */
    public static ListingSearchQuery forSet(String setNumber) {
        return new ListingSearchQuery(null, null, null, null, ListingSortOrder.NEWEST, null, setNumber);
    }
}
