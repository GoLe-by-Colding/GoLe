package com.gole.api.review.domain.exception;

import com.gole.api.common.exception.ConflictException;

/**
 * 한 주문에 이미 후기가 존재할 때의 도메인 예외. (요구사항 R2.4)
 */
public class DuplicateReviewException extends ConflictException {

    public DuplicateReviewException(String orderId) {
        super("DUPLICATE_REVIEW", "이 주문에는 이미 후기를 남겼습니다");
    }
}
