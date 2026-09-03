package com.gole.api.discovery.adapter.in.web;

import com.gole.api.discovery.domain.model.WishlistEntry;
import com.gole.api.discovery.domain.model.WishlistTargetType;
import com.gole.api.listing.domain.model.Listing;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public final class DiscoveryDtos {

    private DiscoveryDtos() {}

    public record FollowRequest(@NotBlank String sellerId) {}

    public record WishlistRequest(@NotNull WishlistTargetType targetType, @NotBlank String targetId) {}

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
            Instant createdAt) {

        public static ListingSummaryResponse from(Listing l) {
            return new ListingSummaryResponse(
                    l.getId(),
                    l.getSellerId(),
                    l.getTitle(),
                    l.getPrice().amount(),
                    l.getCondition().name().toLowerCase(),
                    l.getCatalogSetNumber(),
                    l.getCategory().name().toLowerCase(),
                    l.getStatus().name().toLowerCase(),
                    l.getPhotoUrls(),
                    l.getCreatedAt());
        }
    }

    public record WishlistEntryResponse(String targetType, String targetId) {

        public static WishlistEntryResponse from(WishlistEntry e) {
            return new WishlistEntryResponse(e.targetType().name().toLowerCase(), e.targetId());
        }
    }
}
