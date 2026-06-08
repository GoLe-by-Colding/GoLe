package com.gole.api.review.domain.model;

import com.gole.api.review.domain.exception.InvalidRatingException;
import java.time.Instant;
import java.util.Objects;

/**
 * 거래 후기 애그리거트. 완료된 주문에 대해 구매자가 판매자에게 남기는 평점/후기를 캡슐화. (요구사항 R1)
 * 순수 도메인 모델로 외부 의존이 없으며, 평점 범위 검증을 스스로 보장한다.
 */
public final class Review {

    private static final int MIN_RATING = 1;
    private static final int MAX_RATING = 5;
    private static final int MAX_CONTENT_LENGTH = 1000;

    private final String id;
    private final String orderId;
    private final String reviewerId;
    private final String revieweeId;
    private final int rating;
    private final String content;
    private final Instant createdAt;

    public Review(
            String id,
            String orderId,
            String reviewerId,
            String revieweeId,
            int rating,
            String content,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.orderId = requireText(orderId, "orderId");
        this.reviewerId = requireText(reviewerId, "reviewerId");
        this.revieweeId = requireText(revieweeId, "revieweeId");
        this.rating = requireValidRating(rating);
        this.content = requireContent(content);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /** 신규 후기 작성. revieweeId(판매자)는 주문에서 파생되어 전달된다. (요구사항 R1.1, R1.4) */
    public static Review write(
            String id,
            String orderId,
            String reviewerId,
            String revieweeId,
            int rating,
            String content,
            Instant now) {
        return new Review(id, orderId, reviewerId, revieweeId, rating, content, now);
    }

    private static int requireValidRating(int rating) {
        if (rating < MIN_RATING || rating > MAX_RATING) {
            throw new InvalidRatingException(rating); // 요구사항 R1.2
        }
        return rating;
    }

    private static String requireContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("content must not exceed " + MAX_CONTENT_LENGTH + " chars");
        }
        return content;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public String getId() {
        return id;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getReviewerId() {
        return reviewerId;
    }

    public String getRevieweeId() {
        return revieweeId;
    }

    public int getRating() {
        return rating;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
