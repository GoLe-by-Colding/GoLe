package com.gole.api.bid.domain.model;

import com.gole.api.bid.domain.exception.BidErrors;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * 구매 입찰 — "이 세트를 이 상태로 이 가격이면 사겠다". (buy-bids D1~D9)
 *
 * <p>불변 객체다. 상태 전이는 새 사본을 돌려주고, 저장소가 원본을 조건으로 원자 갱신한다. 만료는 스케줄러 없이
 * 읽을 때 계산한다({@link #effectiveStatus}).
 */
public final class Bid {

    public static final long MIN_PRICE = 1L;
    public static final long MAX_PRICE = 100_000_000L;
    public static final Set<Integer> ALLOWED_DURATION_DAYS = Set.of(7, 30, 60);
    public static final int DEFAULT_DURATION_DAYS = 30;

    private final String id;
    private final String bidderId;
    private final String setNumber;
    private final BidCondition condition;
    private final long price;
    private final int durationDays;
    private final BidStatus status;
    private final Instant createdAt;
    /** 마지막으로 가격·기간을 건 시각. 만료는 여기서 센다(D9). 처음에는 {@code createdAt}과 같다. */
    private final Instant placedAt;

    private final Instant expiresAt;
    private final Instant closedAt; // nullable — 취소·체결 시각
    private final String filledListingId; // nullable
    private final String offerId; // nullable — 체결로 생긴 수락 제안

    public Bid(
            String id,
            String bidderId,
            String setNumber,
            BidCondition condition,
            long price,
            int durationDays,
            BidStatus status,
            Instant createdAt,
            Instant placedAt,
            Instant expiresAt,
            Instant closedAt,
            String filledListingId,
            String offerId) {
        this.id = requireText(id, "id");
        this.bidderId = requireText(bidderId, "bidderId");
        this.setNumber = requireText(setNumber, "setNumber");
        this.condition = Objects.requireNonNull(condition, "condition");
        this.price = price;
        this.durationDays = durationDays;
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.placedAt = Objects.requireNonNull(placedAt, "placedAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.closedAt = closedAt;
        this.filledListingId = filledListingId;
        this.offerId = offerId;
    }

    /** 새 입찰. 가격·기간을 검증한다. (D2) */
    public static Bid place(
            String id,
            String bidderId,
            String setNumber,
            BidCondition condition,
            long price,
            int durationDays,
            Instant now) {
        requireValidPrice(price);
        requireValidDuration(durationDays);
        return new Bid(
                id,
                bidderId,
                setNumber,
                condition,
                price,
                durationDays,
                BidStatus.ACTIVE,
                now,
                now,
                now.plus(Duration.ofDays(durationDays)),
                null,
                null,
                null);
    }

    public static void requireValidPrice(long price) {
        if (price < MIN_PRICE || price > MAX_PRICE) {
            throw BidErrors.priceInvalid();
        }
    }

    public static void requireValidDuration(int durationDays) {
        if (!ALLOWED_DURATION_DAYS.contains(durationDays)) {
            throw BidErrors.durationInvalid();
        }
    }

    /** 만료를 반영한 유효 상태. 만료 시각 그 순간부터 만료로 본다. (D9) */
    public BidStatus effectiveStatus(Instant now) {
        if (status == BidStatus.ACTIVE && !now.isBefore(expiresAt)) {
            return BidStatus.EXPIRED;
        }
        return status;
    }

    public boolean isActiveAt(Instant now) {
        return effectiveStatus(now) == BidStatus.ACTIVE;
    }

    /** 응답용 사본. 저장 상태 자리에 유효 상태를 넣는다. */
    public Bid asOf(Instant now) {
        BidStatus effective = effectiveStatus(now);
        return effective == status ? this : with(effective, placedAt, expiresAt, closedAt, filledListingId, offerId);
    }

    /**
     * 같은 세트·상태에 다시 걸면 새로 만들지 않고 가격과 만료를 갱신한다. (D3) 생성 시각은 그대로 둔다.
     */
    public Bid replace(long newPrice, int newDurationDays, Instant now) {
        requireValidPrice(newPrice);
        requireValidDuration(newDurationDays);
        requireActive(now);
        return new Bid(
                id,
                bidderId,
                setNumber,
                condition,
                newPrice,
                newDurationDays,
                BidStatus.ACTIVE,
                createdAt,
                now,
                now.plus(Duration.ofDays(newDurationDays)),
                null,
                null,
                null);
    }

    /** 입찰자 취소. (D4) */
    public Bid cancel(String actorId, Instant now) {
        if (!bidderId.equals(actorId)) {
            throw BidErrors.accessDenied();
        }
        requireActive(now);
        return with(BidStatus.CANCELED, placedAt, expiresAt, now, null, null);
    }

    /** 판매자가 이 입찰가에 팔았다. 체결 매물을 남긴다. (D7) */
    public Bid fill(String listingId, Instant now) {
        requireActive(now);
        return with(BidStatus.FILLED, placedAt, expiresAt, now, requireText(listingId, "listingId"), null);
    }

    /** 체결로 생긴 수락 제안을 붙인다. 체결된 입찰에만. */
    public Bid withOffer(String newOfferId) {
        if (status != BidStatus.FILLED) {
            throw new IllegalStateException("체결된 입찰에만 제안을 붙인다");
        }
        return with(status, placedAt, expiresAt, closedAt, filledListingId, requireText(newOfferId, "offerId"));
    }

    /** 제안 생성이 실패해 체결을 되돌린 사본. 만료 시각은 원래대로다. */
    public Bid reopen() {
        if (status != BidStatus.FILLED) {
            throw new IllegalStateException("체결된 입찰만 되돌린다");
        }
        return with(BidStatus.ACTIVE, placedAt, expiresAt, null, null, null);
    }

    /** 저장 상태를 만료로 바꾼 사본. 같은 자리의 새 입찰을 위해 유일 인덱스 자리를 비울 때만 쓴다. */
    public Bid expire() {
        return with(BidStatus.EXPIRED, placedAt, expiresAt, closedAt, filledListingId, offerId);
    }

    private void requireActive(Instant now) {
        if (!isActiveAt(now)) {
            throw BidErrors.notActive();
        }
    }

    private Bid with(
            BidStatus nextStatus,
            Instant nextPlacedAt,
            Instant nextExpiresAt,
            Instant nextClosedAt,
            String nextFilledListingId,
            String nextOfferId) {
        return new Bid(
                id,
                bidderId,
                setNumber,
                condition,
                price,
                durationDays,
                nextStatus,
                createdAt,
                nextPlacedAt,
                nextExpiresAt,
                nextClosedAt,
                nextFilledListingId,
                nextOfferId);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public String id() {
        return id;
    }

    public String bidderId() {
        return bidderId;
    }

    public String setNumber() {
        return setNumber;
    }

    public BidCondition condition() {
        return condition;
    }

    public long price() {
        return price;
    }

    public int durationDays() {
        return durationDays;
    }

    public BidStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant placedAt() {
        return placedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant closedAt() {
        return closedAt;
    }

    public String filledListingId() {
        return filledListingId;
    }

    public String offerId() {
        return offerId;
    }
}
