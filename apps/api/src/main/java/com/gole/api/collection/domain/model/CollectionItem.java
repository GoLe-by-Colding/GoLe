package com.gole.api.collection.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 컬렉션 항목: 사용자-카탈로그세트 보유 관계. (요구사항 11.1)
 */
public record CollectionItem(
        String id, String userId, String setNumber, OwnershipStatus status, Instant createdAt) {

    public CollectionItem {
        Objects.requireNonNull(id, "id");
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (setNumber == null || setNumber.isBlank()) {
            throw new IllegalArgumentException("setNumber must not be blank");
        }
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public boolean isOwned() {
        return status == OwnershipStatus.OWNED;
    }
}
