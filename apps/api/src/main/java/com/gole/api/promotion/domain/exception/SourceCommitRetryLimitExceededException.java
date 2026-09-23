package com.gole.api.promotion.domain.exception;

import com.gole.api.common.exception.ConflictException;

/**
 * 같은 릴리스 SHA로 만든 초안이 재시도 상한에 닿았을 때(promotion-review D2/D11).
 *
 * <p>반려가 릴리스 점유를 놓아주므로 그 릴리스는 다시 후보가 된다 — 그 반대급부로 에이전트가
 * 탐색 창(7일) 안에서 매일 같은 릴리스를 다시 집는다. 사람이 이미 여러 번 반려한 릴리스는
 * 다음 시도도 반려될 가능성이 크고 그 사이 유료 모델 호출만 쌓이므로 여기서 끊는다.
 *
 * <p>{@link SourceCommitAlreadyPromotedException}과 코드를 나눈다 — 저쪽은 "지금 붙잡고 있는
 * 초안이 있으니 나중에 다시"이고 이쪽은 "이 릴리스는 그만"이라 호출자가 취할 행동이 다르다.
 */
public class SourceCommitRetryLimitExceededException extends ConflictException {

    public SourceCommitRetryLimitExceededException(String sourceCommitSha, int limit) {
        super(
                "PROMOTION_POST_RETRY_LIMIT",
                "Promotion post retry limit (" + limit + ") reached for source commit: " + sourceCommitSha);
    }
}
