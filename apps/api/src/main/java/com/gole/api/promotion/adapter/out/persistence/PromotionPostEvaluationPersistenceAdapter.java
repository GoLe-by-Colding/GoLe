package com.gole.api.promotion.adapter.out.persistence;

import com.gole.api.promotion.application.port.out.PromotionPostEvaluationRepositoryPort;
import com.gole.api.promotion.domain.model.EvaluationCriterion;
import com.gole.api.promotion.domain.model.EvaluationReasonTag;
import com.gole.api.promotion.domain.model.FirstReviewVerdict;
import com.gole.api.promotion.domain.model.HoldReasonKind;
import com.gole.api.promotion.domain.model.PromotionPostEvaluation;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 홍보 게시물 평가 영속성 어댑터. 도메인 {@link PromotionPostEvaluation}와
 * {@link PromotionPostEvaluationDocument}를 양방향 매핑한다.
 */
@Component
public class PromotionPostEvaluationPersistenceAdapter implements PromotionPostEvaluationRepositoryPort {

    private final PromotionPostEvaluationMongoRepository repository;

    public PromotionPostEvaluationPersistenceAdapter(PromotionPostEvaluationMongoRepository repository) {
        this.repository = repository;
    }

    @Override
    public PromotionPostEvaluation save(PromotionPostEvaluation evaluation) {
        return toDomain(repository.save(toDocument(evaluation)));
    }

    @Override
    public Optional<PromotionPostEvaluation> findByPromotionPostId(String promotionPostId) {
        return repository.findByPromotionPostId(promotionPostId).map(this::toDomain);
    }

    @Override
    public List<PromotionPostEvaluation> findAll() {
        return repository.findAll().stream().map(this::toDomain).toList();
    }

    private PromotionPostEvaluationDocument toDocument(PromotionPostEvaluation evaluation) {
        return new PromotionPostEvaluationDocument(
                evaluation.getId(),
                evaluation.getPromotionPostId(),
                evaluation.getEvaluatorId(),
                evaluation.getEvaluatedAt(),
                evaluation.getCriterionScores().entrySet().stream()
                        .collect(Collectors.toMap(entry -> entry.getKey().name(), Map.Entry::getValue)),
                evaluation.getVerdict().name(),
                evaluation.getHoldReasonKind() == null
                        ? null
                        : evaluation.getHoldReasonKind().name(),
                evaluation.getReasonTags().stream().map(Enum::name).toList(),
                evaluation.getFactualFixNeeded(),
                evaluation.getReviewSeconds(),
                evaluation.getReviseSeconds(),
                evaluation.getNotes());
    }

    private PromotionPostEvaluation toDomain(PromotionPostEvaluationDocument document) {
        Map<EvaluationCriterion, Integer> criterionScores = document.getCriterionScores() == null
                ? Map.of()
                : document.getCriterionScores().entrySet().stream()
                        .collect(Collectors.toMap(
                                entry -> EvaluationCriterion.valueOf(entry.getKey()), Map.Entry::getValue));
        Set<EvaluationReasonTag> reasonTags = document.getReasonTags() == null
                ? Set.of()
                : document.getReasonTags().stream()
                        .map(EvaluationReasonTag::valueOf)
                        .collect(Collectors.toSet());
        return new PromotionPostEvaluation(
                document.getId(),
                document.getPromotionPostId(),
                document.getEvaluatorId(),
                document.getEvaluatedAt(),
                criterionScores,
                FirstReviewVerdict.valueOf(document.getVerdict()),
                document.getHoldReasonKind() == null ? null : HoldReasonKind.valueOf(document.getHoldReasonKind()),
                reasonTags,
                document.getFactualFixNeeded(),
                document.getReviewSeconds(),
                document.getReviseSeconds(),
                document.getNotes());
    }
}
