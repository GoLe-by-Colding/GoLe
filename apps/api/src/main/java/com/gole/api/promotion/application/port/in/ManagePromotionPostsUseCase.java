package com.gole.api.promotion.application.port.in;

import com.gole.api.promotion.domain.model.PromotionPost;
import com.gole.api.promotion.domain.model.PromotionPostStatus;
import java.util.List;

/**
 * 홍보 게시물 검토·발행 유스케이스(관리자 전용). (promotion-review P3~P7)
 */
public interface ManagePromotionPostsUseCase {

    List<PromotionPost> list(PromotionPostStatus status, int limit);

    PromotionPost get(String promotionPostId);

    /**
     * 아직 이 릴리스를 <b>붙잡고 있는</b> 초안이 있는지. 에이전트의 릴리스 탐색이 여기서 멈춘다(D11).
     *
     * <p>이름과 시그니처는 {@code GET /exists} 계약이라 그대로지만 <b>의미가 바뀌었다</b> — 반려된
     * 초안은 릴리스 점유를 놓아주므로(D2) 출처가 같아도 거짓이다. "이 릴리스로 초안이 만들어진 적이
     * 있나"가 아니라 "지금 이 릴리스가 홍보 파이프라인에 올라가 있나"를 답한다. 그래서 반려된
     * 릴리스는 다시 후보가 된다.
     */
    boolean existsBySourceCommitSha(String sourceCommitSha);

    /** 작성자 본인이면 {@code SelfReviewNotAllowedException}. */
    PromotionPost approve(String promotionPostId, String reviewerId);

    /** 작성자 본인이면 {@code SelfReviewNotAllowedException}. */
    PromotionPost reject(String promotionPostId, String reviewerId, String reason);

    /** APPROVED만 발행 가능. 내부에서 {@code SocialPublishPort}를 호출한다. */
    PromotionPost publish(String promotionPostId);
}
