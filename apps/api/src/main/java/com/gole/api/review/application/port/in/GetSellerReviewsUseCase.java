package com.gole.api.review.application.port.in;

import com.gole.api.review.domain.model.Review;
import com.gole.api.review.domain.model.SellerRatingSummary;
import java.util.List;

/**
 * Inbound port: 셀러 후기 목록/평점 요약 조회. (요구사항 R3)
 */
public interface GetSellerReviewsUseCase {

    /** 특정 셀러의 후기를 최신순으로 조회한다. (요구사항 R3.1) */
    List<Review> bySeller(String sellerId);

    /** 특정 셀러의 평점 요약(평균/후기 수). (요구사항 R3.2, R3.3) */
    SellerRatingSummary ratingOf(String sellerId);
}
