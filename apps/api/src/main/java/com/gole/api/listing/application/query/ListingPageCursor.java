package com.gole.api.listing.application.query;

import com.gole.api.listing.domain.model.Listing;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Optional;

/**
 * 매물 검색 페이지의 다음 위치. 마지막으로 내려준 매물의 정렬 키와 ID다. (listing-search-paging P3·P4)
 *
 * <p>정렬마다 키가 다르다 — 최신순은 {@code listedAt}, 가격순은 {@code priceAmount}. 그래서 정렬 이름까지 담은
 * 문자열 하나로 주고받고, 다른 정렬의 커서는 해석하지 않는다(P5).
 */
public record ListingPageCursor(ListingSortOrder sort, Instant listedAt, long price, String id) {

    public static ListingPageCursor after(Listing last, ListingSortOrder sort) {
        return new ListingPageCursor(sort, last.getListedAt(), last.getPrice().amount(), last.getId());
    }

    public String encode() {
        String key = sort == ListingSortOrder.NEWEST ? listedAt.toString() : Long.toString(price);
        String raw = sort.name() + "|" + key + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** 해석할 수 없거나 {@code sort}와 다른 정렬의 커서면 비어 있다. */
    public static Optional<ListingPageCursor> parse(String encoded, ListingSortOrder sort) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 3);
            if (parts.length != 3 || !parts[0].equals(sort.name()) || parts[2].isBlank()) {
                return Optional.empty();
            }
            return Optional.of(
                    sort == ListingSortOrder.NEWEST
                            ? new ListingPageCursor(sort, Instant.parse(parts[1]), 0, parts[2])
                            : new ListingPageCursor(sort, null, Long.parseLong(parts[1]), parts[2]));
        } catch (IllegalArgumentException | DateTimeParseException invalid) {
            return Optional.empty();
        }
    }
}
