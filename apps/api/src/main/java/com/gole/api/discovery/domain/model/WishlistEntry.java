package com.gole.api.discovery.domain.model;

/**
 * 위시리스트 항목. (요구사항 17.1, 17.2)
 */
public record WishlistEntry(String userId, WishlistTargetType targetType, String targetId) {

    public WishlistEntry {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (targetType == null) {
            throw new IllegalArgumentException("targetType must not be null");
        }
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException("targetId must not be blank");
        }
    }
}
