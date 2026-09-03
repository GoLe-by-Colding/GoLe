package com.gole.api.review.application.port.in;

import com.gole.api.review.domain.model.Review;

/** 판매자가 자신에게 작성된 거래 후기에 답글을 남긴다. */
public interface ReplyToReviewUseCase {

    Review reply(ReplyToReviewCommand command);

    record ReplyToReviewCommand(String reviewId, String sellerId, String content) {}
}
