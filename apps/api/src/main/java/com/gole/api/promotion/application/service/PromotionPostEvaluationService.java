package com.gole.api.promotion.application.service;

import com.gole.api.promotion.application.port.in.GetPromotionMetricsUseCase;
import com.gole.api.promotion.application.port.in.RecordPromotionPostEvaluationUseCase;
import com.gole.api.promotion.application.port.out.PromotionAuditMetricsPort;
import com.gole.api.promotion.application.port.out.PromotionPostEvaluationRepositoryPort;
import com.gole.api.promotion.application.port.out.PromotionPostIdGeneratorPort;
import com.gole.api.promotion.application.port.out.PromotionPostRepositoryPort;
import com.gole.api.promotion.domain.exception.PromotionPostEvaluationNotFoundException;
import com.gole.api.promotion.domain.exception.PromotionPostNotFoundException;
import com.gole.api.promotion.domain.model.EvaluationCriterion;
import com.gole.api.promotion.domain.model.FirstReviewVerdict;
import com.gole.api.promotion.domain.model.HoldReasonKind;
import com.gole.api.promotion.domain.model.PromotionPostEvaluation;
import com.gole.api.promotion.domain.model.PromotionPostStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;

/**
 * 홍보 게시물 평가 입력·집계 서비스. (promotion-review/eval.md)
 *
 * <p>지표 계산은 하루 최대 몇 건 수준의 소량 데이터를 전제로 한다. MongoDB aggregation
 * 파이프라인을 따로 유지보수하는 비용이 이 규모에서 얻는 이득보다 커서, 저장소에서 전량을 읽어
 * 애플리케이션 레이어(자바 스트림)에서 계산한다.
 */
@Service
public class PromotionPostEvaluationService
        implements RecordPromotionPostEvaluationUseCase, GetPromotionMetricsUseCase {

    private final PromotionPostRepositoryPort promotionPosts;
    private final PromotionPostEvaluationRepositoryPort evaluations;
    private final PromotionPostIdGeneratorPort idGenerator;
    private final PromotionAuditMetricsPort auditMetrics;
    private final Clock clock;

    public PromotionPostEvaluationService(
            PromotionPostRepositoryPort promotionPosts,
            PromotionPostEvaluationRepositoryPort evaluations,
            PromotionPostIdGeneratorPort idGenerator,
            PromotionAuditMetricsPort auditMetrics,
            Clock clock) {
        this.promotionPosts = promotionPosts;
        this.evaluations = evaluations;
        this.idGenerator = idGenerator;
        this.auditMetrics = auditMetrics;
        this.clock = clock;
    }

    @Override
    public PromotionPostEvaluation upsert(String promotionPostId, String evaluatorId, EvaluationCommand command) {
        promotionPosts.findById(promotionPostId).orElseThrow(() -> new PromotionPostNotFoundException(promotionPostId));
        // 게시물당 평가는 1건 — 기존 평가가 있으면 같은 id를 재사용해 덮어쓴다(promotion-review/eval.md).
        String id = evaluations
                .findByPromotionPostId(promotionPostId)
                .map(PromotionPostEvaluation::getId)
                .orElseGet(idGenerator::newId);
        PromotionPostEvaluation evaluation = PromotionPostEvaluation.record(
                id,
                promotionPostId,
                evaluatorId,
                Instant.now(clock),
                command.criterionScores(),
                command.verdict(),
                command.holdReasonKind(),
                command.reasonTags(),
                command.factualFixNeeded(),
                command.reviewSeconds(),
                command.reviseSeconds(),
                command.notes());
        return evaluations.save(evaluation);
    }

    @Override
    public PromotionPostEvaluation get(String promotionPostId) {
        return evaluations
                .findByPromotionPostId(promotionPostId)
                .orElseThrow(() -> new PromotionPostEvaluationNotFoundException(promotionPostId));
    }

    @Override
    public PromotionMetrics getMetrics() {
        return new PromotionMetrics(operationalMetrics(), qualityMetrics());
    }

    private OperationalMetrics operationalMetrics() {
        Map<PromotionPostStatus, Long> countByStatus = new EnumMap<>(PromotionPostStatus.class);
        for (PromotionPostStatus status : PromotionPostStatus.values()) {
            countByStatus.put(status, promotionPosts.countByStatus(status));
        }

        long approveCount = auditMetrics.countApprove();
        long rejectCount = auditMetrics.countReject();
        long publishCount = auditMetrics.countPublish();
        // 반려율은 PromotionPost 필드가 아니라 감사 로그로 계산한다 — reject() 이후 submitForReview()가
        // reviewerId/reviewedAt/rejectionReason을 지우지 않아 최신 상태만으로는 반려 이력을 셀 수 없다.
        long reviewedDecisions = approveCount + rejectCount;
        Double rejectionRate = reviewedDecisions == 0 ? null : (double) rejectCount / reviewedDecisions;

        List<Long> reviewDurationSeconds = promotionPosts.findAll().stream()
                .filter(post -> post.getSubmittedAt() != null && post.getReviewedAt() != null)
                .map(post -> Duration.between(post.getSubmittedAt(), post.getReviewedAt())
                        .getSeconds())
                .toList();

        return new OperationalMetrics(
                Map.copyOf(countByStatus),
                approveCount,
                rejectCount,
                publishCount,
                rejectionRate,
                durationStats(reviewDurationSeconds));
    }

    private QualityMetrics qualityMetrics() {
        List<PromotionPostEvaluation> all = evaluations.findAll();
        long total = all.size();

        Double adoptionRate = rate(
                all,
                total,
                e -> e.getVerdict() == FirstReviewVerdict.USE_AS_IS || e.getVerdict() == FirstReviewVerdict.MINOR_EDIT);
        Double confirmedDefectRate = rate(
                all,
                total,
                e -> e.getVerdict() == FirstReviewVerdict.HOLD
                        && e.getHoldReasonKind() == HoldReasonKind.CONFIRMED_DEFECT);
        Double evidenceGapRate = rate(
                all,
                total,
                e -> e.getVerdict() == FirstReviewVerdict.HOLD && e.getHoldReasonKind() == HoldReasonKind.EVIDENCE_GAP);
        Double factualFixNeededRate = rate(all, total, e -> Boolean.TRUE.equals(e.getFactualFixNeeded()));

        List<Long> reviewSeconds = all.stream()
                .map(PromotionPostEvaluation::getReviewSeconds)
                .filter(Objects::nonNull)
                .map(Integer::longValue)
                .toList();
        List<Long> reviseSeconds = all.stream()
                .map(PromotionPostEvaluation::getReviseSeconds)
                .filter(Objects::nonNull)
                .map(Integer::longValue)
                .toList();

        return new QualityMetrics(
                total,
                adoptionRate,
                confirmedDefectRate,
                evidenceGapRate,
                factualFixNeededRate,
                criterionScoreDistribution(all),
                durationStats(reviewSeconds),
                durationStats(reviseSeconds));
    }

    /** 분모(평가 건수) 0이면 "0%"로 잘못 표시되지 않게 {@code null}(N/A)을 돌려준다. */
    private static Double rate(
            List<PromotionPostEvaluation> all, long total, Predicate<PromotionPostEvaluation> predicate) {
        if (total == 0) {
            return null;
        }
        long matched = all.stream().filter(predicate).count();
        return (double) matched / total;
    }

    private static Map<EvaluationCriterion, CriterionScoreDistribution> criterionScoreDistribution(
            List<PromotionPostEvaluation> all) {
        Map<EvaluationCriterion, CriterionScoreDistribution> result = new EnumMap<>(EvaluationCriterion.class);
        for (EvaluationCriterion criterion : EvaluationCriterion.values()) {
            long score0 = 0;
            long score1 = 0;
            long score2 = 0;
            long notApplicable = 0;
            for (PromotionPostEvaluation evaluation : all) {
                Integer score = evaluation.getCriterionScores().get(criterion);
                if (score == null) {
                    notApplicable++;
                } else {
                    switch (score) {
                        case 0 -> score0++;
                        case 1 -> score1++;
                        default -> score2++;
                    }
                }
            }
            result.put(criterion, new CriterionScoreDistribution(score0, score1, score2, notApplicable));
        }
        return Map.copyOf(result);
    }

    /** 분모 0이면 median/max 모두 {@code null}(N/A). */
    private static DurationStats durationStats(List<Long> secondsList) {
        if (secondsList.isEmpty()) {
            return new DurationStats(null, null);
        }
        List<Long> sorted = new ArrayList<>(secondsList);
        sorted.sort(Comparator.naturalOrder());
        long max = sorted.get(sorted.size() - 1);
        int size = sorted.size();
        int mid = size / 2;
        long median = size % 2 == 0 ? Math.round((sorted.get(mid - 1) + sorted.get(mid)) / 2.0) : sorted.get(mid);
        return new DurationStats(median, max);
    }
}
