package com.gole.api.promotion.application.port.in;

import com.gole.api.promotion.domain.model.PromotionDraftRequest;
import java.util.Optional;

/**
 * 에이전트가 대기 중인 요청을 집어가고 결과를 회신한다(promotion-review D20).
 *
 * <p>접수({@link RequestPromotionDraftUseCase})와 나눈 이유는 그쪽 주석에 있다.
 */
public interface ConsumePromotionDraftRequestUseCase {

    /** 대기 중인 요청 하나를 점유한다. 없으면 비어 있다. 경쟁은 저장소의 원자 갱신이 막는다. */
    Optional<PromotionDraftRequest> claimNext();

    PromotionDraftRequest succeed(String requestId, String leaseToken, String promotionPostId);

    /** 사유는 코드만 받는다 — 에이전트 예외 원문에는 diff·캡션이 섞일 수 있다. */
    PromotionDraftRequest fail(String requestId, String leaseToken, String failureCode);
}
