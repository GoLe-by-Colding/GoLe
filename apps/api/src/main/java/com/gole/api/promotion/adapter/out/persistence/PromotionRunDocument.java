package com.gole.api.promotion.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** 홍보 에이전트 실행 원장 MongoDB 도큐먼트(promotion-review D23). */
@Document(collection = "promotion_runs")
public class PromotionRunDocument {

    @Id
    private String id;

    // 실행 1건 = 1줄 — @Id 에는 규약상 unique 인덱스를 걸지 않으므로 runKey 에 건다.
    @Indexed(unique = true)
    private String runKey;

    private String category;
    private String sourceCommitSha;
    private String outcome;
    private String reasonCode;
    private String detail;
    private String promotionPostId;
    private String agentSha;
    private String runUrl;
    private List<CallDocument> calls;

    @Indexed
    private Instant recordedAt;

    /** 게이트웨이 호출 한 번의 사용량. */
    public record CallDocument(
            String engine,
            String model,
            boolean ok,
            long inputTokens,
            long cachedInputTokens,
            long outputTokens,
            Double costUsd,
            Long durationMs) {}

    protected PromotionRunDocument() {}

    public PromotionRunDocument(
            String id,
            String runKey,
            String category,
            String sourceCommitSha,
            String outcome,
            String reasonCode,
            String detail,
            String promotionPostId,
            String agentSha,
            String runUrl,
            List<CallDocument> calls,
            Instant recordedAt) {
        this.id = id;
        this.runKey = runKey;
        this.category = category;
        this.sourceCommitSha = sourceCommitSha;
        this.outcome = outcome;
        this.reasonCode = reasonCode;
        this.detail = detail;
        this.promotionPostId = promotionPostId;
        this.agentSha = agentSha;
        this.runUrl = runUrl;
        this.calls = calls;
        this.recordedAt = recordedAt;
    }

    public String getId() {
        return id;
    }

    public String getRunKey() {
        return runKey;
    }

    public String getCategory() {
        return category;
    }

    public String getSourceCommitSha() {
        return sourceCommitSha;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getDetail() {
        return detail;
    }

    public String getPromotionPostId() {
        return promotionPostId;
    }

    public String getAgentSha() {
        return agentSha;
    }

    public String getRunUrl() {
        return runUrl;
    }

    public List<CallDocument> getCalls() {
        return calls;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
