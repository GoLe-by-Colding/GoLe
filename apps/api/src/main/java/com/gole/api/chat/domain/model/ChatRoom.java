package com.gole.api.chat.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 채팅방. 매물 기반(listingId) 구매자↔판매자 1:1 대화.
 * (buyerId, sellerId) 쌍으로 중복 생성을 방지한다.
 */
public record ChatRoom(String id, String listingId, String buyerId, String sellerId, Instant createdAt) {

    public ChatRoom {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(listingId, "listingId");
        Objects.requireNonNull(buyerId, "buyerId");
        Objects.requireNonNull(sellerId, "sellerId");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
