package com.gole.api.promotion.application.port.out;

import com.gole.api.promotion.domain.model.PromotionPost;
import com.gole.api.promotion.domain.model.PromotionPostStatus;
import java.util.List;
import java.util.Optional;

/**
 * 홍보 게시물 영속성 출력 포트.
 */
public interface PromotionPostRepositoryPort {

    PromotionPost save(PromotionPost promotionPost);

    Optional<PromotionPost> findById(String promotionPostId);

    boolean existsBySourceCommitSha(String sourceCommitSha);

    List<PromotionPost> findRecentFirst(PromotionPostStatus status, int limit);

    long countByStatus(PromotionPostStatus status);

    /** 지표 집계용 — 데이터 양이 적어(하루 최대 몇 건) 애플리케이션 레이어에서 reduce한다. */
    List<PromotionPost> findAll();
}
