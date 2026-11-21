package com.gole.api.review.domain.exception;

import com.gole.api.common.exception.DomainException;

/**
 * 평점이 1~5 범위를 벗어났을 때의 도메인 예외. (요구사항 R1.2)
 */
public class InvalidRatingException extends DomainException {

    public InvalidRatingException(int rating) {
        super("INVALID_RATING", "별점은 1점부터 5점까지 고를 수 있습니다");
    }
}
