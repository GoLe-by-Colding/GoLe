package com.gole.api.listing.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 매물 문의 댓글(Q&A). 작성자는 서버가 검증한 세션 계정이고, 본문은 1~1000자다.
 *
 * <p>삭제는 지우지 않고 {@code deleted} 로 표시한다 — 공개 목록에서만 빠진다.
 */
public record ListingComment(
        String id, String listingId, String authorId, String content, boolean deleted, Instant createdAt) {

    public static final int MAX_CONTENT_LENGTH = 1000;

    public ListingComment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(listingId, "listingId");
        Objects.requireNonNull(authorId, "authorId");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    /** 새 문의 댓글. */
    public static ListingComment post(String id, String listingId, String authorId, String content, Instant now) {
        return new ListingComment(id, listingId, authorId, content, false, now);
    }
}
