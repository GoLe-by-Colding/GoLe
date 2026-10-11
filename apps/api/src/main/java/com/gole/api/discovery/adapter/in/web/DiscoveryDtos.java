package com.gole.api.discovery.adapter.in.web;

import com.gole.api.discovery.domain.model.DiscoveredListing;
import com.gole.api.discovery.domain.model.WishlistEntry;
import com.gole.api.discovery.domain.model.WishlistTargetType;
import com.gole.api.media.domain.model.MediaKey;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public final class DiscoveryDtos {

    private DiscoveryDtos() {}

    public record FollowRequest(@NotBlank String sellerId) {}

    public record WishlistRequest(
            @NotNull WishlistTargetType targetType,
            @NotBlank String targetId) {}

    public record ListingSummaryResponse(
            String id,
            String sellerId,
            String title,
            long price,
            String condition,
            String catalogSetNumber,
            String category,
            String status,
            List<String> photoUrls,
            Instant createdAt,
            Instant listedAt,
            Long previousPrice) {

        /**
         * @see com.gole.api.listing.adapter.in.web.ListingResponse 같은 의미의 필드.
         *     {@code listedAt}은 정렬 키(등록 또는 마지막 끌올), {@code previousPrice}는 지금 가격이
         *     직전보다 쌀 때만 있는 직전가다. (listing-edit-and-bump R2)
         */
        public static ListingSummaryResponse from(DiscoveredListing l) {
            return new ListingSummaryResponse(
                    l.id(),
                    l.sellerId(),
                    l.title(),
                    l.price(),
                    l.condition().toLowerCase(),
                    l.catalogSetNumber(),
                    l.category().toLowerCase(),
                    l.status().toLowerCase(),
                    l.photoUrls().stream()
                            .flatMap(value -> MediaKey.safePublicPath(value).stream())
                            .toList(),
                    l.createdAt(),
                    l.listedAt(),
                    l.previousPrice());
        }
    }

    public record WishlistEntryResponse(String targetType, String targetId) {

        public static WishlistEntryResponse from(WishlistEntry e) {
            return new WishlistEntryResponse(e.targetType().name().toLowerCase(), e.targetId());
        }
    }
}
