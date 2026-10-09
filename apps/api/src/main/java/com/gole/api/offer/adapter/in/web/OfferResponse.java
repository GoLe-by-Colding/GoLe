package com.gole.api.offer.adapter.in.web;

import com.gole.api.offer.domain.model.PriceOffer;
import java.time.Instant;

/**
 * 가격 제안 응답. (price-offer O15)
 *
 * @param roomId 입찰에서 온 제안은 null
 * @param origin {@code chat} | {@code bid}
 * @param status 유효 상태. {@code pending} | {@code accepted} | {@code declined} | {@code withdrawn} | {@code expired}
 * @param respondedAt 대기 중이면 null
 */
public record OfferResponse(
        String id,
        String listingId,
        String roomId,
        String buyerId,
        String sellerId,
        long price,
        long listingPriceAtOffer,
        String origin,
        String status,
        Instant createdAt,
        Instant respondedAt,
        Instant expiresAt) {

    /** 유스케이스가 돌려준 제안은 이미 유효 상태로 맞춰져 있다. */
    public static OfferResponse from(PriceOffer offer) {
        return new OfferResponse(
                offer.id(),
                offer.listingId(),
                offer.roomId(),
                offer.buyerId(),
                offer.sellerId(),
                offer.price(),
                offer.listingPriceAtOffer(),
                offer.origin().key(),
                offer.status().key(),
                offer.createdAt(),
                offer.respondedAt(),
                offer.expiresAt());
    }
}
