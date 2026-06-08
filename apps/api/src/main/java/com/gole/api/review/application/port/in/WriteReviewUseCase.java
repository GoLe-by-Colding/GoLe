package com.gole.api.review.application.port.in;

import com.gole.api.review.domain.model.Review;

/**
 * Inbound port: 후기 작성. (요구사항 R1, R2)
 */
public interface WriteReviewUseCase {

    /** 후기를 작성하고 영속된 후기를 반환한다. */
    Review write(WriteReviewCommand command);

    record WriteReviewCommand(String orderId, String reviewerId, int rating, String content) {
    }
}
