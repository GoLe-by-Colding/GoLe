package com.gole.api.review.domain.exception;

import com.gole.api.common.exception.ForbiddenException;

/**
 * 자기 자신에게 후기를 남기려는 시도. (요구사항 R2.5)
 *
 * <p>자기거래 주문은 주문 생성 단계에서 차단되지만, 차단 이전에 쌓인 주문이
 * 남아 있을 수 있으므로 후기 작성에서도 독립적으로 막는다.
 */
public class SelfReviewException extends ForbiddenException {

    public SelfReviewException(String orderId) {
        super("SELF_REVIEW_NOT_ALLOWED", "Reviewers cannot review themselves for order: " + orderId);
    }
}
