package com.gole.api.discovery.domain.model;

/**
 * 팔로우 관계(사용자 → 셀러). (요구사항 16.3)
 */
public record Follow(String userId, String sellerId) {

    public Follow {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (sellerId == null || sellerId.isBlank()) {
            throw new IllegalArgumentException("sellerId must not be blank");
        }
    }
}
