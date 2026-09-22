package com.gole.api.promotion.application.port.in;

import com.gole.api.promotion.domain.model.PromotionDraftRequest;

/**
 * 관리자가 홍보 초안 생성을 요청한다(promotion-review D20).
 *
 * <p><b>소비({@link ConsumePromotionDraftRequestUseCase})와 일부러 나눴다.</b> 이쪽 호출자는
 * 사람이고 저쪽은 봇이다. 봇 계정이 지금은 완전한 ADMIN 이라(T11) 나중에 권한을 좁힐 때
 * 경계가 이미 그어져 있어야 한다.
 */
public interface RequestPromotionDraftUseCase {

    /**
     * 요청을 접수한다.
     *
     * <p>접수 시점에 "이 릴리스를 홍보해도 되는가"를 <b>여기서 끝까지 검사한다.</b> 유료 모델
     * 호출을 낭비하기 전에 막는 것도 있지만, 더 큰 이유는 지금 Python 스캐너가 그 사유를
     * 조용히 삼켜 관리자에게 아무 일도 안 일어난 것처럼 보이기 때문이다(D20).
     *
     * @param sourceCommitSha 비우면 에이전트가 자동 선정한다
     */
    PromotionDraftRequest request(String sourceCommitSha, String requestedBy);

    java.util.List<PromotionDraftRequest> findRecentFirst(int limit);
}
