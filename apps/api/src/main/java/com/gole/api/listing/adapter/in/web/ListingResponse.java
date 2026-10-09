package com.gole.api.listing.adapter.in.web;

import com.gole.api.listing.domain.model.ConditionDisclosure;
import com.gole.api.listing.domain.model.Listing;
import com.gole.api.media.domain.model.MediaKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 리스팅 응답 DTO. 프론트엔드 entities/listing 타입과 형태가 일치한다.
 *
 * <p>끌올·수정 필드(listing-edit-and-bump R1):
 * <ul>
 *   <li>{@code listedAt} — "최신순" 정렬 키. 등록 시각이었다가 끌올하면 그 시각이 된다.
 *   <li>{@code bumpedAt} — 마지막 끌올 시각. 한 번도 안 했으면 null.
 *   <li>{@code bumpAvailableAt} — 다음 끌올 가능 시각(= {@code listedAt} + 쿨다운).
 *   <li>{@code previousPrice}·{@code priceChangedAt} — 지금 가격이 직전보다 쌀 때만 직전가가 있다.
 *   <li>{@code photoKeys} — 수정 폼이 다시 제출할 저장 키. {@code photoUrls}(공개 경로)를 그대로
 *       돌려보내면 미디어 참조 검증에서 거부된다. 사용자 업로드가 아닌 키(데모 커버 등)는 빠진다.
 * </ul>
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
        String interestTag,
        String status,
        Instant createdAt,
        Instant listedAt,
        Instant bumpedAt,
        Instant bumpAvailableAt,
        Long previousPrice,
        Instant priceChangedAt,
        List<String> photoKeys) {

    public static ListingResponse from(Listing listing, Duration bumpCooldown) {
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
                listing.getPhotoUrls().stream()
                        .flatMap(value -> MediaKey.safePublicPath(value).stream())
                        .toList(),
                listing.getCatalogSetNumber(),
                listing.getCategory().key(),
                listing.getInterestTag() == null
                        ? null
                        : listing.getInterestTag().key(),
                listing.getStatus().name().toLowerCase(),
                listing.getCreatedAt(),
                listing.getListedAt(),
                listing.getBumpedAt(),
                listing.bumpAvailableAt(bumpCooldown),
                listing.getPreviousPrice() == null
                        ? null
                        : listing.getPreviousPrice().amount(),
                listing.getPriceChangedAt(),
                listing.getPhotoUrls().stream()
                        .flatMap(value -> MediaKey.safeStoredKey(value).stream())
                        .toList());
    }
}
