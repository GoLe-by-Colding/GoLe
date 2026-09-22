package com.gole.api.promotion.domain.exception;

import com.gole.api.common.exception.ConflictException;

/**
 * 회신하려는 실행이 더 이상 이 요청을 점유하고 있지 않을 때(promotion-review D20).
 *
 * <p>에이전트가 멈춰 있는 사이 lease 가 만료돼 다른 실행이 같은 요청을 집어간 경우다. 늦게
 * 돌아온 쪽의 결과를 받으면 이긴 실행의 결과를 덮어쓰므로 거절한다.
 */
public class DraftRequestLeaseLostException extends ConflictException {

    public DraftRequestLeaseLostException(String requestId) {
        super("PROMOTION_DRAFT_REQUEST_LEASE_LOST", "Promotion draft request lease is no longer held: " + requestId);
    }
}
