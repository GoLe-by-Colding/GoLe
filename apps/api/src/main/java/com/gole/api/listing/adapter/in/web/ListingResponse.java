package com.gole.api.listing.adapter.in.web;

import com.gole.api.listing.domain.model.ConditionDisclosure;
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
        String completeness,
        boolean hasBox,
        boolean hasManual,
        boolean hasMissingParts,
        String missingPartsNote,
        String defectsNote,
        List<String> photoUrls,
        String catalogSetNumber,
        String category,
        String status,
        Instant createdAt) {

    public static ListingResponse from(Listing listing) {
        ConditionDisclosure d = listing.getDisclosure();
        return new ListingResponse(
                listing.getId(),
                listing.getSellerId(),
                listing.getTitle(),
                listing.getDescription(),
                listing.getPrice().amount(),
                listing.getCondition().name().toLowerCase(),
                d.completeness().name().toLowerCase(),
                d.hasBox(),
                d.hasManual(),
                d.hasMissingParts(),
                d.missingPartsNote(),
                d.defectsNote(),
                listing.getPhotoUrls(),
                listing.getCatalogSetNumber(),
                listing.getCategory().key(),
                listing.getStatus().name().toLowerCase(),
                listing.getCreatedAt());
    }
}
