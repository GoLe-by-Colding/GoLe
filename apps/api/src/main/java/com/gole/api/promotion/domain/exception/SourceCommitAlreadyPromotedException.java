package com.gole.api.promotion.domain.exception;

import com.gole.api.common.exception.ConflictException;

/**
 * 같은 릴리스 SHA로 이미 홍보 초안이 만들어져 있을 때(promotion-review D11/P11).
 *
 * <p>에이전트는 {@code GET /exists} 로 확인한 뒤 {@code POST} 로 만든다 — 그 두 왕복 사이에
 * 다른 실행이 끼어들면 같은 릴리스가 두 번 홍보된다. 생성 시점에 서버가 한 번 더 막는다.
 */
public class SourceCommitAlreadyPromotedException extends ConflictException {

    public SourceCommitAlreadyPromotedException(String sourceCommitSha) {
        super(
                "PROMOTION_POST_DUPLICATE_SOURCE_COMMIT",
                "Promotion post already exists for source commit: " + sourceCommitSha);
    }
}
