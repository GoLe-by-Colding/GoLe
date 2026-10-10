package com.gole.api.chat.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 매물 채팅방. 매물 기반(listingId) 구매자↔판매자 1:1 대화이고, (구매자, 판매자, 매물) 한 쌍에 방은 하나다.
 *
 * <p>직거래 완료는 양쪽이 각각 확인해야 끝난다. 확인·완료 시각은 저장소가 조건부로만 기록한다(동시 요청에도 한 번).
 * 레거시 방은 {@code listingId} 가 비어 있을 수 있다 — 그런 방에서는 직거래를 완료하지 않는다.
 */
public record ChatRoom(
        String id,
        String listingId,
        String buyerId,
        String sellerId,
        Instant createdAt,
        Instant lastMessageAt,
        Instant buyerConfirmedAt,
        Instant sellerConfirmedAt,
        Instant directTradeCompletedAt) {

    /** 직거래 확인의 주체. */
    public enum Party {
        BUYER,
        SELLER
    }

    public ChatRoom {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(buyerId, "buyerId");
        Objects.requireNonNull(sellerId, "sellerId");
        Objects.requireNonNull(createdAt, "createdAt");
        if (lastMessageAt == null) {
            lastMessageAt = createdAt;
        }
    }

    /** 새 매물 방. 마지막 활동 시각은 개설 시각이다. */
    public static ChatRoom open(String id, String listingId, String buyerId, String sellerId, Instant now) {
        Objects.requireNonNull(listingId, "listingId");
        return new ChatRoom(id, listingId, buyerId, sellerId, now, now, null, null, null);
    }

    public boolean isParticipant(String accountId) {
        return buyerId.equals(accountId) || sellerId.equals(accountId);
    }

    /** 참여자의 직거래 확인 주체. 참여자가 아니면 판단하지 않는다 — 먼저 {@link #isParticipant} 로 거른다. */
    public Party partyOf(String accountId) {
        return buyerId.equals(accountId) ? Party.BUYER : Party.SELLER;
    }

    public String counterpartyOf(String accountId) {
        return buyerId.equals(accountId) ? sellerId : buyerId;
    }

    public boolean hasListing() {
        return listingId != null && !listingId.isBlank();
    }

    public boolean bothConfirmed() {
        return buyerConfirmedAt != null && sellerConfirmedAt != null;
    }

    public boolean directTradeCompleted() {
        return directTradeCompletedAt != null;
    }
}
