package com.gole.api.promotion.domain.exception;

import com.gole.api.common.exception.ConflictException;

/**
 * 검토 대기 초안이 상한에 닿아 새 요청을 받지 않을 때(promotion-review D18/D20).
 *
 * <p>에이전트도 같은 상한에서 조기 반환하므로, 이걸 접수 단계에서 막지 않으면 요청만 쌓이고
 * 아무 일도 일어나지 않는다. 사람이 밀린 초안을 먼저 처리해야 풀린다.
 */
public class ReviewQueueFullException extends ConflictException {

    public ReviewQueueFullException(int limit) {
        super(
                "PROMOTION_REVIEW_QUEUE_FULL",
                "Pending promotion reviews reached the limit of " + limit + "; review them before requesting more");
    }
}
