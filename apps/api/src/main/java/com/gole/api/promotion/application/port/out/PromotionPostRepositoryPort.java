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

    /**
     * 아직 이 릴리스를 <b>점유 중인</b> 초안이 있는지 — 구현은 {@code claimedSourceCommitSha}를 본다.
     *
     * <p>반려된 초안은 점유를 놓아주므로(D2), 출처({@code sourceCommitSha})가 같아도 여기서는
     * 거짓이다. 이름은 인바운드 포트·{@code GET /exists} 계약과 맞추려고 그대로 뒀다.
     */
    boolean existsBySourceCommitSha(String sourceCommitSha);

    /** 같은 <b>출처</b> 릴리스로 만들어진 초안 수 — 반려된 것까지 센다. 재시도 상한 판정용. */
    long countBySourceCommitSha(String sourceCommitSha);

    List<PromotionPost> findRecentFirst(PromotionPostStatus status, int limit);

    long countByStatus(PromotionPostStatus status);

    /** 지표 집계용 — 데이터 양이 적어(하루 최대 몇 건) 애플리케이션 레이어에서 reduce한다. */
    List<PromotionPost> findAll();
}
