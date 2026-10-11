package com.gole.api.offer.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 가격 제안 MongoDB 영속 모델. 도메인 {@code PriceOffer}와 분리되어 있고 매핑은
 * {@link OfferPersistenceAdapter}가 한다.
 *
 * <p>{@code uq_offer_pending_listing_buyer}는 저장 상태 {@code PENDING}만 대상으로 하는 부분 유일
 * 인덱스다. 같은 매물·구매자의 대기 제안이 동시에 두 건 들어와도 하나만 저장된다. 만료된 {@code PENDING}은
 * 새 제안 전에 {@code EXPIRED}로 원자 전이해 자리를 비운다.
 */
@Document(collection = "offers")
@CompoundIndexes({
    @CompoundIndex(name = "offer_listing_buyer_created_idx", def = "{'listingId': 1, 'buyerId': 1, 'createdAt': -1}"),
    @CompoundIndex(name = "offer_room_created_idx", def = "{'roomId': 1, 'createdAt': -1}"),
    @CompoundIndex(name = "offer_listing_created_idx", def = "{'listingId': 1, 'createdAt': -1}"),
    @CompoundIndex(
            name = "uq_offer_pending_listing_buyer",
            def = "{'listingId': 1, 'buyerId': 1}",
            unique = true,
            partialFilter = "{'status': 'PENDING'}")
})
public class OfferDocument {

    @Id
    private String id;

    private String listingId;
    private String roomId; // nullable — 입찰에서 온 제안
    private String buyerId;
    private String sellerId;
    private long price;
    private long listingPriceAtOffer;
    private String origin;
    private String status;
    private Instant createdAt;
    private Instant respondedAt; // nullable
    private Instant expiresAt;

    protected OfferDocument() {
        // MongoDB 매핑용
    }

    public OfferDocument(
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
        this.id = id;
        this.listingId = listingId;
        this.roomId = roomId;
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.price = price;
        this.listingPriceAtOffer = listingPriceAtOffer;
        this.origin = origin;
        this.status = status;
        this.createdAt = createdAt;
        this.respondedAt = respondedAt;
        this.expiresAt = expiresAt;
    }

    public String getId() {
        return id;
    }

    public String getListingId() {
        return listingId;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getBuyerId() {
        return buyerId;
    }

    public String getSellerId() {
        return sellerId;
    }

    public long getPrice() {
        return price;
    }

    public long getListingPriceAtOffer() {
        return listingPriceAtOffer;
    }

    public String getOrigin() {
        return origin;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
