package com.gole.api.promotion.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 홍보 게시물 MongoDB 도큐먼트.
 */
@Document(collection = "promotion_posts")
public class PromotionPostDocument {

    @Id
    private String id;

    private String channel;
    private String caption;
    private List<String> mediaUrls;

    @Indexed
    private String authorId;

    /**
     * 릴리스 SHA 멱등 키(D11/P11). 서비스의 사전 존재 검사만으로는 에이전트의 "조회 후 생성"
     * 두 왕복 사이에 같은 SHA 두 건이 들어올 수 있어, 마지막 방어선을 DB 인덱스에 둔다.
     *
     * <p><b>{@code sparse} 가 빠지면 안 된다.</b> MongoDB 의 unique 인덱스는 없는 필드를 null 로
     * 취급하므로, {@code sourceCommitSha} 가 null 인 사람이 직접 쓴 초안은 저장소 전체에 단
     * 하나만 존재할 수 있게 된다 — 두 번째 초안부터 duplicate key 로 깨진다.
     * {@code chat/SocialChatRoomDocument.dedupeKey} 가 같은 이유로 sparse 다.
     *
     * <p><b>배포 전 한 번:</b> {@code auto-index-creation: true} 라 기동할 때 인덱스를 만드는데,
     * 이미 옛 비-unique {@code sourceCommitSha_1} 이 깔린 환경에서는 MongoDB 가
     * {@code IndexOptionsConflict}(85)로 거부하고 애플리케이션 기동 자체가 실패한다.
     * 그 환경에서는 먼저 {@code db.promotion_posts.dropIndex("sourceCommitSha_1")} 을 한 번 돌린다.
     */
    @Indexed(unique = true, sparse = true)
    private String sourceCommitSha;

    @Indexed
    private String status;

    private Instant createdAt;
    private Instant submittedAt;
    private String reviewerId;
    private Instant reviewedAt;
    private String rejectionReason;
    private Instant publishedAt;
    private String externalPostId;

    protected PromotionPostDocument() {}

    public PromotionPostDocument(
            String id,
            String channel,
            String caption,
            List<String> mediaUrls,
            String authorId,
            String sourceCommitSha,
            String status,
            Instant createdAt,
            Instant submittedAt,
            String reviewerId,
            Instant reviewedAt,
            String rejectionReason,
            Instant publishedAt,
            String externalPostId) {
        this.id = id;
        this.channel = channel;
        this.caption = caption;
        this.mediaUrls = mediaUrls;
        this.authorId = authorId;
        this.sourceCommitSha = sourceCommitSha;
        this.status = status;
        this.createdAt = createdAt;
        this.submittedAt = submittedAt;
        this.reviewerId = reviewerId;
        this.reviewedAt = reviewedAt;
        this.rejectionReason = rejectionReason;
        this.publishedAt = publishedAt;
        this.externalPostId = externalPostId;
    }

    public String getId() {
        return id;
    }

    public String getChannel() {
        return channel;
    }

    public String getCaption() {
        return caption;
    }

    public List<String> getMediaUrls() {
        return mediaUrls;
    }

    public String getAuthorId() {
        return authorId;
    }

    public String getSourceCommitSha() {
        return sourceCommitSha;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public String getReviewerId() {
        return reviewerId;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public String getExternalPostId() {
        return externalPostId;
    }
}
