package com.gole.api.promotion.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 홍보 초안 품질 평가 애그리거트 — 사람이 채점한 루브릭 점수와 첫 검토 판정을 담는다.
 * 게시물당 평가는 1건이며, 다시 기록하면 같은 id로 덮어쓴다(promotion-review/eval.md).
 *
 * <p>채점 자체는 여기서 하지 않는다. 이 모델은 사람이 입력한 결과를 검증·보관할 뿐이고,
 * 집계는 {@code GetPromotionMetricsUseCase}가 담당한다.
 */
public final class PromotionPostEvaluation {

    private static final int MIN_SCORE = 0;
    private static final int MAX_SCORE = 2;

    private final String id;
    private final String promotionPostId;
    private final String evaluatorId;
    private final Instant evaluatedAt;
    private final Map<EvaluationCriterion, Integer> criterionScores;
    private final FirstReviewVerdict verdict;
    private final HoldReasonKind holdReasonKind;
    private final Set<EvaluationReasonTag> reasonTags;
    private final Boolean factualFixNeeded;
    private final Integer reviewSeconds;
    private final Integer reviseSeconds;
    private final String notes;

    public PromotionPostEvaluation(
            String id,
            String promotionPostId,
            String evaluatorId,
            Instant evaluatedAt,
            Map<EvaluationCriterion, Integer> criterionScores,
            FirstReviewVerdict verdict,
            HoldReasonKind holdReasonKind,
            Set<EvaluationReasonTag> reasonTags,
            Boolean factualFixNeeded,
            Integer reviewSeconds,
            Integer reviseSeconds,
            String notes) {
        this.id = Objects.requireNonNull(id, "id");
        this.promotionPostId = requireText(promotionPostId, "promotionPostId");
        this.evaluatorId = requireText(evaluatorId, "evaluatorId");
        this.evaluatedAt = Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        this.verdict = Objects.requireNonNull(verdict, "verdict");
        this.criterionScores = requireCriterionScores(criterionScores);
        this.holdReasonKind = requireHoldReasonKind(holdReasonKind, verdict);
        this.reasonTags = reasonTags == null ? Set.of() : Set.copyOf(reasonTags);
        this.factualFixNeeded = factualFixNeeded;
        this.reviewSeconds = requireNonNegative(reviewSeconds, "reviewSeconds");
        this.reviseSeconds = requireNonNegative(reviseSeconds, "reviseSeconds");
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
    }

    /** 평가 기록: 평가 시각은 호출 시점으로 고정한다. 필드 검증은 생성자가 전담한다. */
    public static PromotionPostEvaluation record(
            String id,
            String promotionPostId,
            String evaluatorId,
            Instant evaluatedAt,
            Map<EvaluationCriterion, Integer> criterionScores,
            FirstReviewVerdict verdict,
            HoldReasonKind holdReasonKind,
            Set<EvaluationReasonTag> reasonTags,
            Boolean factualFixNeeded,
            Integer reviewSeconds,
            Integer reviseSeconds,
            String notes) {
        return new PromotionPostEvaluation(
                id,
                promotionPostId,
                evaluatorId,
                evaluatedAt,
                criterionScores,
                verdict,
                holdReasonKind,
                reasonTags,
                factualFixNeeded,
                reviewSeconds,
                reviseSeconds,
                notes);
    }

    private static Map<EvaluationCriterion, Integer> requireCriterionScores(Map<EvaluationCriterion, Integer> scores) {
        if (scores == null || scores.isEmpty()) {
            return Map.of();
        }
        for (Map.Entry<EvaluationCriterion, Integer> entry : scores.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "criterionScores key");
            Integer value = entry.getValue();
            if (value == null || value < MIN_SCORE || value > MAX_SCORE) {
                throw new IllegalArgumentException("criterionScores value for " + entry.getKey() + " must be between "
                        + MIN_SCORE + " and " + MAX_SCORE);
            }
        }
        return Map.copyOf(scores);
    }

    /**
     * {@code holdReasonKind}는 {@link FirstReviewVerdict#HOLD}일 때만 값을 가질 수 있다. HOLD가
     * 아닌데 값이 채워져 있으면 잘못된 입력이다 — 그 외에는 값이 없어도(아직 세부 사유 미확인)
     * 허용한다.
     */
    private static HoldReasonKind requireHoldReasonKind(HoldReasonKind holdReasonKind, FirstReviewVerdict verdict) {
        if (holdReasonKind != null && verdict != FirstReviewVerdict.HOLD) {
            throw new IllegalArgumentException("holdReasonKind must be null unless verdict is HOLD");
        }
        return holdReasonKind;
    }

    private static Integer requireNonNegative(Integer value, String name) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(name + " must be zero or positive");
        }
        return value;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
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

    public Map<EvaluationCriterion, Integer> getCriterionScores() {
        return criterionScores;
    }

    public FirstReviewVerdict getVerdict() {
        return verdict;
    }

    public HoldReasonKind getHoldReasonKind() {
        return holdReasonKind;
    }

    public Set<EvaluationReasonTag> getReasonTags() {
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
