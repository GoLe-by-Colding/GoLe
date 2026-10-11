package com.gole.api.offer.domain.model;

import com.gole.api.offer.domain.exception.OfferErrors;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 가격 제안(네고) 애그리거트. 불변이며 전이 메서드는 새 인스턴스를 돌려준다.
 *
 * <p>상태는 두 겹이다. {@link #status()}는 저장된 상태이고, {@link #effectiveStatus(Instant)}는 만료를
 * 반영한 유효 상태다(price-offer O12). 열린 상태({@code PENDING}·{@code ACCEPTED})라도 {@code expiresAt}
 * 이 지나면 유효 상태는 {@code EXPIRED}다. 스케줄러가 없으므로 판단은 언제나 유효 상태로 한다.
 *
 * <p>영속성 계층의 원자적 갱신은 저장 상태와 {@code expiresAt}을 조건으로 건다. 그래서 전이 메서드는
 * 저장소에서 막 읽은 인스턴스에만 호출한다 — {@link #asOf(Instant)}로 만든 응답용 사본은 저장 상태가
 * 바뀌어 있어 조건이 맞지 않는다(맞지 않으면 갱신이 실패하는 쪽으로 안전하다).
 */
public final class PriceOffer {

    private final String id;
    private final String listingId;
    /** 채팅 제안의 방. 입찰에서 온 제안은 null. */
    private final String roomId;

    private final String buyerId;
    private final String sellerId;
    private final long price;
    /** 제안 시점의 매물가. 할인율 표시와 사후 검토용 기록이며 주문 금액 계산에는 쓰지 않는다. */
    private final long listingPriceAtOffer;

    private final OfferOrigin origin;
    private final OfferStatus status;
    private final Instant createdAt;
    /** 판매자 수락·거절 또는 구매자 철회 시각. 대기 중이면 null. */
    private final Instant respondedAt;
    /** 대기 제안은 생성 + 대기 TTL, 수락 제안은 수락 + 수락 TTL. */
    private final Instant expiresAt;

    public PriceOffer(
            String id,
            String listingId,
            String roomId,
            String buyerId,
            String sellerId,
            long price,
            long listingPriceAtOffer,
            OfferOrigin origin,
            OfferStatus status,
            Instant createdAt,
            Instant respondedAt,
            Instant expiresAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.listingId = Objects.requireNonNull(listingId, "listingId");
        this.roomId = roomId;
        this.buyerId = Objects.requireNonNull(buyerId, "buyerId");
        this.sellerId = Objects.requireNonNull(sellerId, "sellerId");
        this.price = price;
        this.listingPriceAtOffer = listingPriceAtOffer;
        this.origin = Objects.requireNonNull(origin, "origin");
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.respondedAt = respondedAt;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    /**
     * 채팅방 구매자의 새 제안. (price-offer O1, O4)
     *
     * <p>가격은 {@code 0 < price < 매물가}여야 한다. 매물가 이상의 제안은 그냥 매물가로 사면 되므로
     * 받지 않는다.
     */
    public static PriceOffer propose(
            String id,
            String listingId,
            String roomId,
            String buyerId,
            String sellerId,
            long price,
            long listingPrice,
            Instant now,
            Duration pendingTtl) {
        requireDifferentParties(buyerId, sellerId);
        if (price <= 0 || price >= listingPrice) {
            throw OfferErrors.priceInvalid();
        }
        return new PriceOffer(
                id,
                listingId,
                Objects.requireNonNull(roomId, "roomId"),
                buyerId,
                sellerId,
                price,
                listingPrice,
                OfferOrigin.CHAT,
                OfferStatus.PENDING,
                now,
                null,
                now.plus(pendingTtl));
    }

    /**
     * 방 없이 바로 수락 상태로 만드는 제안. 입찰 체결이 쓴다. (price-offer O21)
     *
     * <p>가격이 매물가 이상이어도 된다 — 주문 금액은 {@code min(제안가, 매물가)}라서 매물가를 넘지 않는다.
     */
    public static PriceOffer preAccepted(
            String id,
            String listingId,
            String buyerId,
            String sellerId,
            long price,
            long listingPrice,
            OfferOrigin origin,
            Instant now,
            Duration acceptedTtl) {
        requireDifferentParties(buyerId, sellerId);
        if (price <= 0) {
            throw OfferErrors.priceInvalid();
        }
        return new PriceOffer(
                id,
                listingId,
                null,
                buyerId,
                sellerId,
                price,
                listingPrice,
                origin,
                OfferStatus.ACCEPTED,
                now,
                now,
                now.plus(acceptedTtl));
    }

    /** 만료를 반영한 유효 상태. 만료 시각 그 순간부터 만료로 본다. */
    public OfferStatus effectiveStatus(Instant now) {
        if (status.isOpen() && !now.isBefore(expiresAt)) {
            return OfferStatus.EXPIRED;
        }
        return status;
    }

    /** 응답용 사본. 저장 상태 자리에 유효 상태를 넣는다. */
    public PriceOffer asOf(Instant now) {
        OfferStatus effective = effectiveStatus(now);
        return effective == status ? this : with(effective, respondedAt, expiresAt);
    }

    /** 판매자 수락. 유효 {@code PENDING}이어야 하고, 만료를 수락 시각 + 수락 TTL로 다시 잡는다. (O7) */
    public PriceOffer accept(Instant now, Duration acceptedTtl) {
        if (effectiveStatus(now) != OfferStatus.PENDING) {
            throw OfferErrors.notPending();
        }
        return with(OfferStatus.ACCEPTED, now, now.plus(acceptedTtl));
    }

    /** 판매자 거절 또는 수락 취소. (O8) */
    public PriceOffer decline(Instant now) {
        requireOpen(now);
        return with(OfferStatus.DECLINED, now, expiresAt);
    }

    /** 구매자 철회. (O9) */
    public PriceOffer withdraw(Instant now) {
        requireOpen(now);
        return with(OfferStatus.WITHDRAWN, now, expiresAt);
    }

    /** 이 매물·이 구매자의 유효 {@code ACCEPTED} 제안인가 — 주문에 쓸 수 있는가. (O16) */
    public boolean isUsableFor(String listingId, String buyerId, Instant now) {
        return this.listingId.equals(listingId)
                && this.buyerId.equals(buyerId)
                && effectiveStatus(now) == OfferStatus.ACCEPTED;
    }

    public boolean isParty(String accountId) {
        return buyerId.equals(accountId) || sellerId.equals(accountId);
    }

    /** 판매자만 할 수 있는 응답. 구매자를 포함해 판매자가 아니면 모두 거부한다. (O7, O8, O10) */
    public void requireSeller(String actorId) {
        if (!sellerId.equals(actorId)) {
            throw OfferErrors.accessDenied();
        }
    }

    /** 구매자만 할 수 있는 철회. (O9, O10) */
    public void requireBuyer(String actorId) {
        if (!buyerId.equals(actorId)) {
            throw OfferErrors.accessDenied();
        }
    }

    private void requireOpen(Instant now) {
        if (!effectiveStatus(now).isOpen()) {
            throw OfferErrors.notOpen();
        }
    }

    private PriceOffer with(OfferStatus nextStatus, Instant nextRespondedAt, Instant nextExpiresAt) {
        return new PriceOffer(
                id,
                listingId,
                roomId,
                buyerId,
                sellerId,
                price,
                listingPriceAtOffer,
                origin,
                nextStatus,
                createdAt,
                nextRespondedAt,
                nextExpiresAt);
    }

    private static void requireDifferentParties(String buyerId, String sellerId) {
        if (Objects.equals(buyerId, sellerId)) {
            // 자기 매물 제안은 자전거래 시세 조작의 첫 단계다. 주문 쪽 자기거래 가드보다 먼저 막는다.
            throw OfferErrors.buyerOnly();
        }
    }

    public String id() {
        return id;
    }

    public String listingId() {
        return listingId;
    }

    public String roomId() {
        return roomId;
    }

    public String buyerId() {
        return buyerId;
    }

    public String sellerId() {
        return sellerId;
    }

    public long price() {
        return price;
    }

    public long listingPriceAtOffer() {
        return listingPriceAtOffer;
    }

    public OfferOrigin origin() {
        return origin;
    }

    /** 저장된 상태. 판단에는 {@link #effectiveStatus(Instant)}를 쓴다. */
    public OfferStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant respondedAt() {
        return respondedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }
}
