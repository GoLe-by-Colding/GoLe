package com.gole.api.promotion.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 홍보 게시물 평가 MongoDB 도큐먼트.
 */
@Document(collection = "promotion_post_evaluations")
public class PromotionPostEvaluationDocument {

    @Id
    private String id;

    // 게시물당 평가 1건 — @Id에는 규약상 unique 인덱스를 걸지 않으므로(_id가 이미 unique) 별도
    // 필드에 unique 인덱스를 건다.
    @Indexed(unique = true)
    private String promotionPostId;

    private String evaluatorId;
    private Instant evaluatedAt;
    private Map<String, Integer> criterionScores;
    private String verdict;
    private String holdReasonKind;
    private List<String> reasonTags;
    private Boolean factualFixNeeded;
    private Integer reviewSeconds;
    private Integer reviseSeconds;
    private String notes;

    protected PromotionPostEvaluationDocument() {}

    public PromotionPostEvaluationDocument(
            String id,
            String promotionPostId,
            String evaluatorId,
            Instant evaluatedAt,
            Map<String, Integer> criterionScores,
            String verdict,
            String holdReasonKind,
            List<String> reasonTags,
            Boolean factualFixNeeded,
            Integer reviewSeconds,
            Integer reviseSeconds,
            String notes) {
        this.id = id;
        this.promotionPostId = promotionPostId;
        this.evaluatorId = evaluatorId;
        this.evaluatedAt = evaluatedAt;
        this.criterionScores = criterionScores;
        this.verdict = verdict;
        this.holdReasonKind = holdReasonKind;
        this.reasonTags = reasonTags;
        this.factualFixNeeded = factualFixNeeded;
        this.reviewSeconds = reviewSeconds;
        this.reviseSeconds = reviseSeconds;
        this.notes = notes;
    }

    public String getId() {
        return id;
    }

    public String getPromotionPostId() {
        return promotionPostId;
    }

    public String getEvaluatorId() {
        return evaluatorId;
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }

    public Map<String, Integer> getCriterionScores() {
        return criterionScores;
    }

    public String getVerdict() {
        return verdict;
    }

    public String getHoldReasonKind() {
        return holdReasonKind;
    }

    public List<String> getReasonTags() {
        return reasonTags;
    }

    public Boolean getFactualFixNeeded() {
        return factualFixNeeded;
    }

    public Integer getReviewSeconds() {
        return reviewSeconds;
    }

    public Integer getReviseSeconds() {
        return reviseSeconds;
    }

    public String getNotes() {
        return notes;
    }
}
