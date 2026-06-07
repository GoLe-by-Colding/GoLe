package com.gole.api.community.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 게시글 댓글. (요구사항 12.3)
 */
public record Comment(String id, String postId, String authorId, String content, Instant createdAt) {

    public Comment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(postId, "postId");
        if (authorId == null || authorId.isBlank()) {
            throw new IllegalArgumentException("authorId must not be blank");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
