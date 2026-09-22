package com.gole.api.promotion.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.promotion.application.port.in.GetPromotionMetricsUseCase.PromotionMetrics;
import com.gole.api.promotion.application.port.in.RecordPromotionPostEvaluationUseCase.EvaluationCommand;
import com.gole.api.promotion.application.port.out.PromotionAuditMetricsPort;
import com.gole.api.promotion.application.port.out.PromotionPostEvaluationRepositoryPort;
import com.gole.api.promotion.application.port.out.PromotionPostIdGeneratorPort;
import com.gole.api.promotion.application.port.out.PromotionPostRepositoryPort;
import com.gole.api.promotion.application.port.out.PromotionPostRepositoryPort.ReviewTimestamps;
import com.gole.api.promotion.domain.exception.PromotionPostEvaluationNotFoundException;
import com.gole.api.promotion.domain.exception.PromotionPostNotFoundException;
import com.gole.api.promotion.domain.model.EvaluationCriterion;
import com.gole.api.promotion.domain.model.EvaluationReasonTag;
import com.gole.api.promotion.domain.model.FirstReviewVerdict;
import com.gole.api.promotion.domain.model.HoldReasonKind;
import com.gole.api.promotion.domain.model.PromotionChannel;
import com.gole.api.promotion.domain.model.PromotionPost;
import com.gole.api.promotion.domain.model.PromotionPostEvaluation;
import com.gole.api.promotion.domain.model.PromotionPostStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PromotionPostEvaluationServiceTest {

    private final PromotionPostRepositoryPort promotionPosts = mock(PromotionPostRepositoryPort.class);
    private final PromotionPostEvaluationRepositoryPort evaluations = mock(PromotionPostEvaluationRepositoryPort.class);
    private final PromotionPostIdGeneratorPort idGenerator = mock(PromotionPostIdGeneratorPort.class);
    private final PromotionAuditMetricsPort auditMetrics = mock(PromotionAuditMetricsPort.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-20T00:00:00Z"), ZoneOffset.UTC);
    private final PromotionPostEvaluationService service =
            new PromotionPostEvaluationService(promotionPosts, evaluations, idGenerator, auditMetrics, clock);

    private static PromotionPost draft() {
        return PromotionPost.draft(
                "promo-1", PromotionChannel.THREADS, "캡션", List.of(), "author-1", null, Instant.EPOCH);
    }

    private static EvaluationCommand basicCommand(FirstReviewVerdict verdict) {
        return new EvaluationCommand(
                Map.of(EvaluationCriterion.FACT_BASIS, 2), verdict, null, Set.of(), false, 60, 0, null);
    }

    @Test
    void upsertCreatesNewEvaluationWhenNoneExists() {
        when(promotionPosts.findById("promo-1")).thenReturn(Optional.of(draft()));
        when(evaluations.findByPromotionPostId("promo-1")).thenReturn(Optional.empty());
        when(idGenerator.newId()).thenReturn("eval-1");
        when(evaluations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PromotionPostEvaluation result =
                service.upsert("promo-1", "admin-1", basicCommand(FirstReviewVerdict.USE_AS_IS));

        assertThat(result.getId()).isEqualTo("eval-1");
        assertThat(result.getPromotionPostId()).isEqualTo("promo-1");
        assertThat(result.getEvaluatorId()).isEqualTo("admin-1");
        assertThat(result.getEvaluatedAt()).isEqualTo(Instant.parse("2026-09-20T00:00:00Z"));
    }

    @Test
    void upsertOverwritesExistingEvaluationWithSameId() {
        when(promotionPosts.findById("promo-1")).thenReturn(Optional.of(draft()));
        PromotionPostEvaluation existing = PromotionPostEvaluation.record(
                "eval-1",
                "promo-1",
                "admin-0",
                Instant.EPOCH,
                Map.of(),
                FirstReviewVerdict.MAJOR_REWRITE,
                null,
                Set.of(),
                null,
                null,
                null,
                null);
        when(evaluations.findByPromotionPostId("promo-1")).thenReturn(Optional.of(existing));
        when(evaluations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PromotionPostEvaluation result =
                service.upsert("promo-1", "admin-1", basicCommand(FirstReviewVerdict.USE_AS_IS));

        assertThat(result.getId()).isEqualTo("eval-1");
        assertThat(result.getVerdict()).isEqualTo(FirstReviewVerdict.USE_AS_IS);
        assertThat(result.getEvaluatedAt()).isEqualTo(Instant.parse("2026-09-20T00:00:00Z"));
        verify(idGenerator, never()).newId();
    }

    @Test
    void upsertThrowsWhenPromotionPostMissing() {
        when(promotionPosts.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upsert("missing", "admin-1", basicCommand(FirstReviewVerdict.USE_AS_IS)))
                .isInstanceOf(PromotionPostNotFoundException.class);
    }

    @Test
    void upsertRejectsHoldReasonKindWhenVerdictIsNotHold() {
        when(promotionPosts.findById("promo-1")).thenReturn(Optional.of(draft()));
        when(evaluations.findByPromotionPostId("promo-1")).thenReturn(Optional.empty());
        when(idGenerator.newId()).thenReturn("eval-1");
        EvaluationCommand invalid = new EvaluationCommand(
                Map.of(),
                FirstReviewVerdict.USE_AS_IS,
                HoldReasonKind.CONFIRMED_DEFECT,
                Set.of(),
                null,
                null,
                null,
                null);

        assertThatThrownBy(() -> service.upsert("promo-1", "admin-1", invalid))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getThrowsWhenEvaluationMissing() {
        when(evaluations.findByPromotionPostId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("missing")).isInstanceOf(PromotionPostEvaluationNotFoundException.class);
    }

    @Test
    void metricsReturnNullRatesWhenNoEvaluationsRecorded() {
        for (PromotionPostStatus status : PromotionPostStatus.values()) {
            when(promotionPosts.countByStatus(status)).thenReturn(0L);
        }
        when(promotionPosts.findReviewTimestamps()).thenReturn(List.of());
        when(evaluations.findAll()).thenReturn(List.of());
        when(auditMetrics.countApprove()).thenReturn(0L);
        when(auditMetrics.countReject()).thenReturn(0L);
        when(auditMetrics.countPublish()).thenReturn(0L);

        PromotionMetrics metrics = service.getMetrics();

        assertThat(metrics.operational().rejectionRate()).isNull();
        assertThat(metrics.operational().reviewDuration().medianSeconds()).isNull();
        assertThat(metrics.operational().reviewDuration().maxSeconds()).isNull();
        assertThat(metrics.quality().evaluationCount()).isZero();
        assertThat(metrics.quality().firstReviewAdoptionRate()).isNull();
        assertThat(metrics.quality().confirmedDefectRate()).isNull();
        assertThat(metrics.quality().evidenceGapRate()).isNull();
        assertThat(metrics.quality().factualFixNeededRate()).isNull();
        assertThat(metrics.quality().reviewSeconds().medianSeconds()).isNull();
    }

    @Test
    void metricsComputeRejectionRateFromAuditCounts() {
        for (PromotionPostStatus status : PromotionPostStatus.values()) {
            when(promotionPosts.countByStatus(status)).thenReturn(0L);
        }
        when(promotionPosts.findReviewTimestamps()).thenReturn(List.of());
        when(evaluations.findAll()).thenReturn(List.of());
        when(auditMetrics.countApprove()).thenReturn(3L);
        when(auditMetrics.countReject()).thenReturn(1L);
        when(auditMetrics.countPublish()).thenReturn(2L);

        PromotionMetrics metrics = service.getMetrics();

        assertThat(metrics.operational().approveCount()).isEqualTo(3);
        assertThat(metrics.operational().rejectCount()).isEqualTo(1);
        assertThat(metrics.operational().publishCount()).isEqualTo(2);
        assertThat(metrics.operational().rejectionRate()).isEqualTo(0.25);
    }

    @Test
    void metricsComputeQualityRatesFromFixtures() {
        for (PromotionPostStatus status : PromotionPostStatus.values()) {
            when(promotionPosts.countByStatus(status)).thenReturn(0L);
        }
        when(promotionPosts.findReviewTimestamps()).thenReturn(List.of());
        when(auditMetrics.countApprove()).thenReturn(0L);
        when(auditMetrics.countReject()).thenReturn(0L);
        when(auditMetrics.countPublish()).thenReturn(0L);

        PromotionPostEvaluation useAsIs = evaluation("e1", FirstReviewVerdict.USE_AS_IS, null, false);
        PromotionPostEvaluation minorEdit = evaluation("e2", FirstReviewVerdict.MINOR_EDIT, null, true);
        PromotionPostEvaluation confirmedDefect =
                evaluation("e3", FirstReviewVerdict.HOLD, HoldReasonKind.CONFIRMED_DEFECT, null);
        PromotionPostEvaluation evidenceGap =
                evaluation("e4", FirstReviewVerdict.HOLD, HoldReasonKind.EVIDENCE_GAP, null);
        when(evaluations.findAll()).thenReturn(List.of(useAsIs, minorEdit, confirmedDefect, evidenceGap));

        PromotionMetrics metrics = service.getMetrics();

        assertThat(metrics.quality().evaluationCount()).isEqualTo(4);
        assertThat(metrics.quality().firstReviewAdoptionRate()).isEqualTo(0.5);
        assertThat(metrics.quality().confirmedDefectRate()).isEqualTo(0.25);
        assertThat(metrics.quality().evidenceGapRate()).isEqualTo(0.25);
        assertThat(metrics.quality().factualFixNeededRate()).isEqualTo(0.25);
    }

    @Test
    void metricsComputeReviewDurationFromReviewedPromotionPosts() {
        for (PromotionPostStatus status : PromotionPostStatus.values()) {
            when(promotionPosts.countByStatus(status)).thenReturn(0L);
        }
        when(evaluations.findAll()).thenReturn(List.of());
        when(auditMetrics.countApprove()).thenReturn(0L);
        when(auditMetrics.countReject()).thenReturn(0L);
        when(auditMetrics.countPublish()).thenReturn(0L);

        when(promotionPosts.findReviewTimestamps())
                .thenReturn(List.of(new ReviewTimestamps(
                        Instant.parse("2026-09-19T00:00:00Z"), Instant.parse("2026-09-19T00:10:00Z"))));

        PromotionMetrics metrics = service.getMetrics();

        assertThat(metrics.operational().reviewDuration().medianSeconds()).isEqualTo(600);
        assertThat(metrics.operational().reviewDuration().maxSeconds()).isEqualTo(600);
    }

    private static PromotionPostEvaluation evaluation(
            String id, FirstReviewVerdict verdict, HoldReasonKind holdReasonKind, Boolean factualFixNeeded) {
        return PromotionPostEvaluation.record(
                id,
                "promo-" + id,
                "admin-1",
                Instant.EPOCH,
                Map.of(EvaluationCriterion.FACT_BASIS, 2),
                verdict,
                holdReasonKind,
                Set.of(EvaluationReasonTag.OTHER),
                factualFixNeeded,
                30,
                0,
                null);
    }
}
