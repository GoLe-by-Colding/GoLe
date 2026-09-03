package com.gole.api.review.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 후기 MongoDB 영속 모델. 순수 도메인 모델({@code Review})과 분리되어 있으며
 * 매핑은 {@link ReviewPersistenceAdapter}가 담당한다.
 *
 * <p>{@code orderId}는 주문당 1회 작성을 보장하기 위해 유니크 인덱스를 둔다. (요구사항 R2.4)
 */
@Document(collection = "reviews")
@CompoundIndex(name = "reviewee_created_at_idx", def = "{'revieweeId': 1, 'createdAt': -1}")
public class ReviewDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String orderId;

    private String reviewerId;

    @Indexed
    private String revieweeId;

    private int rating;

    private String content;

    private Instant createdAt;

    private String reply;

    private Instant repliedAt;

    private Instant hiddenAt;

    private String hiddenReason;

    protected ReviewDocument() {
        // MongoDB 매핑용
    }

    public ReviewDocument(
            String id,
            String orderId,
            String reviewerId,
            String revieweeId,
            int rating,
            String content,
            Instant createdAt) {
        this(id, orderId, reviewerId, revieweeId, rating, content, createdAt, null, null, null, null);
    }

    public ReviewDocument(
            String id,
            String orderId,
            String reviewerId,
            String revieweeId,
            int rating,
            String content,
            Instant createdAt,
            String reply,
            Instant repliedAt,
            Instant hiddenAt,
            String hiddenReason) {
        this.id = id;
        this.orderId = orderId;
        this.reviewerId = reviewerId;
        this.revieweeId = revieweeId;
        this.rating = rating;
        this.content = content;
        this.createdAt = createdAt;
        this.reply = reply;
        this.repliedAt = repliedAt;
        this.hiddenAt = hiddenAt;
        this.hiddenReason = hiddenReason;
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

    public String getReply() {
        return reply;
    }

    public Instant getRepliedAt() {
        return repliedAt;
    }

    public Instant getHiddenAt() {
        return hiddenAt;
    }

    public String getHiddenReason() {
        return hiddenReason;
    }
}
