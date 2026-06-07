package com.gole.api.listing.adapter.in.web;

import com.gole.api.listing.domain.model.Listing;
import java.time.Instant;
import java.util.List;

/**
 * 리스팅 응답 DTO. 프론트엔드 entities/listing 타입과 형태가 일치한다.
 */
public record ListingResponse(
        String id,
        String sellerId,
        String title,
        String description,
        long price,
        String condition,
        List<String> photoUrls,
        String catalogSetNumber,
        String status,
        Instant createdAt) {

    public static ListingResponse from(Listing listing) {
        return new ListingResponse(
                listing.getId(),
                listing.getSellerId(),
                listing.getTitle(),
                listing.getDescription(),
                listing.getPrice().amount(),
                listing.getCondition().name().toLowerCase(),
                listing.getPhotoUrls(),
                listing.getCatalogSetNumber(),
                listing.getStatus().name().toLowerCase(),
                listing.getCreatedAt());
    }
}
