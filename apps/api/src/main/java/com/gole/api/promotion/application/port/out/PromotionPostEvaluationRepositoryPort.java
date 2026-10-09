package com.gole.api.promotion.application.port.out;

import com.gole.api.promotion.domain.model.PromotionPostEvaluation;
import java.util.List;
import java.util.Optional;

/**
 * 홍보 게시물 평가 영속성 출력 포트.
 */
public interface PromotionPostEvaluationRepositoryPort {

    PromotionPostEvaluation save(PromotionPostEvaluation evaluation);

    Optional<PromotionPostEvaluation> findByPromotionPostId(String promotionPostId);

    /** 지표 집계용 — 데이터 양이 적어(하루 최대 몇 건) 애플리케이션 레이어에서 reduce한다. */
    List<PromotionPostEvaluation> findAll();
}
