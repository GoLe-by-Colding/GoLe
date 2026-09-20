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
     * 출처 릴리스 SHA — 불변이라 반려돼도 남는다. 목록 화면과 집계가 이것을 본다.
     *
     * <p>unique 가 <b>아니다.</b> 반려가 점유를 놓아주므로 같은 릴리스로 초안이 여럿 생길 수 있고
     * (횟수는 서비스의 재시도 상한이 끊는다), 중복 방어선은 아래 {@code claimedSourceCommitSha} 로
     * 옮겼다. 스펙이 옛 {@code sourceCommitSha_1} 과 같아서 {@code IndexKeySpecsConflict}(86)로
     * 기동이 깨지는 일도 없다 — 여기에 {@code unique} 를 얹으면 그때 깨진다.
     */
    @Indexed
    private String sourceCommitSha;

    /**
     * 점유 릴리스 SHA 멱등 키(D11/P11). 서비스의 사전 존재 검사만으로는 에이전트의 "조회 후 생성"
     * 두 왕복 사이에 같은 SHA 두 건이 들어올 수 있어, 마지막 방어선을 DB 인덱스에 둔다.
     *
     * <p><b>{@code sparse} 가 빠지면 안 된다.</b> MongoDB 의 unique 인덱스는 없는 필드를 null 로
     * 취급하므로, 이 값이 null 인 초안(사람이 직접 쓴 것, 그리고 <b>반려된 것</b>)은 저장소 전체에
     * 단 하나만 존재할 수 있게 된다 — 두 번째부터 duplicate key 로 깨진다.
     * {@code chat/SocialChatRoomDocument.dedupeKey} 가 같은 이유로 sparse 다.
     *
     * <p>새 필드라 어느 환경에도 옛 인덱스가 없다. {@code auto-index-creation: true} 가 기동할 때
     * 처음부터 unique 로 만들므로 사전 드롭이 필요 없다.
     */
    @Indexed(unique = true, sparse = true)
    private String claimedSourceCommitSha;

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
            String claimedSourceCommitSha,
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
        this.claimedSourceCommitSha = claimedSourceCommitSha;
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

    public String getClaimedSourceCommitSha() {
        return claimedSourceCommitSha;
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
