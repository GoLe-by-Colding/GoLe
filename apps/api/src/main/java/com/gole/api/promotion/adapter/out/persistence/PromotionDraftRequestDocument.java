package com.gole.api.promotion.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 관리자 콘솔 발 홍보 초안 요청(promotion-review D20).
 *
 * <p><b>{@code sourceCommitSha} 에 unique 를 걸지 않는다.</b> 릴리스 점유의 단일 진실 공급원은
 * {@code promotion_posts.claimedSourceCommitSha} 이고, 여기에 또 걸면 같은 규칙이 두 벌이 되어
 * "반려로 점유가 풀렸는데 요청은 못 만드는" 어긋남이 생긴다. 중복 요청은 서비스가
 * {@code findActive*} 로 막는다.
 */
@Document(collection = "promotion_draft_requests")
@CompoundIndexes({
    @CompoundIndex(name = "promotion_draft_request_due_idx", def = "{'status': 1, 'leaseUntil': 1, 'createdAt': 1}"),
    @CompoundIndex(name = "promotion_draft_request_commit_idx", def = "{'sourceCommitSha': 1, 'status': 1}")
})
public class PromotionDraftRequestDocument {

    @Id
    private String id;

    /** null 이면 에이전트가 자동 선정한다. */
    private String sourceCommitSha;

    private String requestedBy;
    private String status;
    private int attempts;
    private String leaseToken;
    private Instant leaseUntil;
    private String promotionPostId;
    private String failureCode;
    private Instant createdAt;
    private Instant finishedAt;

    /**
     * 종료된 요청은 보존기간 뒤 Mongo TTL 로 지운다.
     *
     * <p>에이전트가 하루 한 번 도므로 요청은 최소 하루는 살아야 한다. 에이전트의
     * {@code RETENTION_DAYS}(7일)와 맞춰 두면 세션 원장과 수명이 같아 추적이 쉽다.
     */
    @Indexed(name = "promotion_draft_request_terminal_ttl", expireAfter = "0s")
    private Instant expiresAt;

    protected PromotionDraftRequestDocument() {}

    public PromotionDraftRequestDocument(
            String id,
            String sourceCommitSha,
            String requestedBy,
            String status,
            int attempts,
            String leaseToken,
            Instant leaseUntil,
            String promotionPostId,
            String failureCode,
            Instant createdAt,
            Instant finishedAt,
            Instant expiresAt) {
        this.id = id;
        this.sourceCommitSha = sourceCommitSha;
        this.requestedBy = requestedBy;
        this.status = status;
        this.attempts = attempts;
        this.leaseToken = leaseToken;
        this.leaseUntil = leaseUntil;
        this.promotionPostId = promotionPostId;
        this.failureCode = failureCode;
        this.createdAt = createdAt;
        this.finishedAt = finishedAt;
        this.expiresAt = expiresAt;
    }

    public String getId() {
        return id;
    }

    public String getSourceCommitSha() {
        return sourceCommitSha;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public String getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLeaseToken() {
        return leaseToken;
    }

    public Instant getLeaseUntil() {
        return leaseUntil;
    }

    public String getPromotionPostId() {
        return promotionPostId;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
