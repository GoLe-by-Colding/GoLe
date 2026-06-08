package com.gole.api.review.domain.exception;

import com.gole.api.common.exception.ConflictException;

/**
 * 한 주문에 이미 후기가 존재할 때의 도메인 예외. (요구사항 R2.4)
 */
public class DuplicateReviewException extends ConflictException {

    public DuplicateReviewException(String orderId) {
        super("DUPLICATE_REVIEW", "A review already exists for order: " + orderId);
    }
}
