package com.gole.api.review.application.port.in;

/** 신고가 인용된 거래 후기를 공개 목록과 평점에서 제외한다. */
public interface ModerateReviewUseCase {

    void hide(String reviewId, String reason);
}
