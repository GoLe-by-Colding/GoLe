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

    /**
     * 이 계정이 대화 당사자인가. 1:1 대화이므로 구매자·판매자만 해당한다.
     *
     * <p>빈 값은 언제나 false다. 인증이 비어 있는 경로로 들어온 요청이 "누구인지 모르니
     * 통과"가 되면 안 된다.
     */
    public boolean isParticipant(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return false;
        }
        return buyerId.equals(accountId) || sellerId.equals(accountId);
    }
}
