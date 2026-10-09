package com.gole.api.promotion.application.port.in;

import com.gole.api.promotion.domain.model.EvaluationCriterion;
import com.gole.api.promotion.domain.model.EvaluationReasonTag;
import com.gole.api.promotion.domain.model.FirstReviewVerdict;
import com.gole.api.promotion.domain.model.HoldReasonKind;
import com.gole.api.promotion.domain.model.PromotionPostEvaluation;
import java.util.Map;
import java.util.Set;

/**
 * 홍보 게시물 품질 평가 입력·조회 유스케이스(관리자 전용). 채점은 사람이 하고, 이 유스케이스는
 * 그 결과를 저장·조회만 한다(promotion-review/eval.md). 게시물당 평가는 1건이며, 같은
 * promotionPostId로 다시 upsert하면 기존 평가를 덮어쓴다.
 */
public interface RecordPromotionPostEvaluationUseCase {

    PromotionPostEvaluation upsert(String promotionPostId, String evaluatorId, EvaluationCommand command);

    /** 없으면 {@code PromotionPostEvaluationNotFoundException}. */
    PromotionPostEvaluation get(String promotionPostId);

    record EvaluationCommand(
            Map<EvaluationCriterion, Integer> criterionScores,
            FirstReviewVerdict verdict,
            HoldReasonKind holdReasonKind,
            Set<EvaluationReasonTag> reasonTags,
            Boolean factualFixNeeded,
            Integer reviewSeconds,
            Integer reviseSeconds,
            String notes) {}
}
