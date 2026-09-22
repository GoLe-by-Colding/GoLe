package com.gole.api.promotion.application.port.in;

import com.gole.api.promotion.domain.model.EvaluationCriterion;
import com.gole.api.promotion.domain.model.PromotionPostStatus;
import java.util.Map;

/**
 * 홍보 운영·품질 지표 조회 유스케이스(관리자 전용). 운영 지표는 {@code PromotionPost}·감사
 * 로그에서, 품질 지표는 사람이 입력한 {@code PromotionPostEvaluation}에서 자동 집계한다
 * (promotion-review/eval.md 집계 지표).
 */
public interface GetPromotionMetricsUseCase {

    PromotionMetrics getMetrics();

    record PromotionMetrics(OperationalMetrics operational, QualityMetrics quality) {}

    /**
     * @param countByStatus 상태별 게시물 건수
     * @param approveCount 감사 로그 기준 승인 건수
     * @param rejectCount 감사 로그 기준 반려 건수
     * @param publishCount 감사 로그 기준 발행 건수
     * @param rejectionRate 반려 / (승인 + 반려). 분모 0이면 {@code null}(N/A)
     * @param reviewDuration 제출~검토 완료 소요시간(초)의 중앙값·최댓값
     */
    record OperationalMetrics(
            Map<PromotionPostStatus, Long> countByStatus,
            long approveCount,
            long rejectCount,
            long publishCount,
            Double rejectionRate,
            DurationStats reviewDuration) {}

    /**
     * @param evaluationCount 평가 완료 건수(집계 분모)
     * @param firstReviewAdoptionRate (그대로 사용 + 경미한 수정) / 평가 건수
     * @param confirmedDefectRate 확정 결함 보류 / 평가 건수
     * @param evidenceGapRate 증거 부족 보류 / 평가 건수
     * @param factualFixNeededRate 사실 수정 필요 / 평가 건수
     * @param criterionScoreDistribution 루브릭 항목별 0/1/2/N/A 건수 분포
     * @param reviewSeconds 검토 소요시간(초)의 중앙값·최댓값
     * @param reviseSeconds 수정 소요시간(초)의 중앙값·최댓값
     */
    record QualityMetrics(
            long evaluationCount,
            Double firstReviewAdoptionRate,
            Double confirmedDefectRate,
            Double evidenceGapRate,
            Double factualFixNeededRate,
            Map<EvaluationCriterion, CriterionScoreDistribution> criterionScoreDistribution,
            DurationStats reviewSeconds,
            DurationStats reviseSeconds) {}

    record CriterionScoreDistribution(long score0, long score1, long score2, long notApplicable) {}

    /** 분모 0이면 median/max 모두 {@code null}(N/A) — "0%"로 잘못 표시되지 않게 한다. */
    record DurationStats(Long medianSeconds, Long maxSeconds) {}
}
