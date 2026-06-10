package com.gole.api.listing.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 매물 문의 댓글(Q&A). 작성자·내용·작성 시각만 갖는 단순 값.
 * 삭제는 "deleted" 상태(소프트 삭제)로 처리한다.
 */
public record ListingComment(
        String id, String listingId, String authorId, String content, boolean deleted, Instant createdAt) {

    public ListingComment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(listingId, "listingId");
        Objects.requireNonNull(authorId, "authorId");
        Objects.requireNonNull(createdAt, "createdAt");
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("comment content must not be blank");
        }
    }
}
