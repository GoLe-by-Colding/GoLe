package com.gole.api.review.domain.exception;

import com.gole.api.common.exception.DomainException;

/**
 * 평점이 1~5 범위를 벗어났을 때의 도메인 예외. (요구사항 R1.2)
 */
public class InvalidRatingException extends DomainException {

    public InvalidRatingException(int rating) {
        super("INVALID_RATING", "Rating must be between 1 and 5 but was " + rating);
    }
}
