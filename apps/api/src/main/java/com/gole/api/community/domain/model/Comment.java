package com.gole.api.community.domain.model;

import java.time.Instant;
import java.util.Objects;

/** 게시글 댓글. 신고 조치 시 원문은 보존하고 공개 조회에서만 제외한다. (요구사항 12.3) */
public record Comment(
        String id,
        String postId,
        String authorId,
        String content,
        Instant createdAt,
        Instant hiddenAt,
        String hiddenReason) {

    public Comment(String id, String postId, String authorId, String content, Instant createdAt) {
        this(id, postId, authorId, content, createdAt, null, null);
    }

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
        hiddenReason = hiddenReason == null ? null : hiddenReason.trim();
        if (hiddenAt != null && (hiddenReason == null || hiddenReason.isBlank())) {
            throw new IllegalArgumentException("hiddenReason must not be blank when comment is hidden");
        }
    }

    public Comment hide(String reason, Instant now) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        return new Comment(id, postId, authorId, content, createdAt, Objects.requireNonNull(now, "now"), reason);
    }

    public boolean isHidden() {
        return hiddenAt != null;
    }
}
