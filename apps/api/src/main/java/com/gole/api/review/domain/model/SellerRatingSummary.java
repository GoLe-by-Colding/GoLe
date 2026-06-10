package com.gole.api.review.domain.model;

import java.util.List;

/**
 * 셀러 단위 평점 요약 값 객체. 평균 평점(소수 1자리 반올림)과 후기 수를 담는다. (요구사항 R3.2, R3.3)
 */
public record SellerRatingSummary(String sellerId, double average, long count) {

    /** 후기 목록으로부터 요약을 계산한다. 빈 목록이면 평균 0.0, 수 0. */
    public static SellerRatingSummary of(String sellerId, List<Review> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            return new SellerRatingSummary(sellerId, 0.0, 0);
        }
        double rawAverage =
                reviews.stream().mapToInt(Review::getRating).average().orElse(0.0);
        double rounded = Math.round(rawAverage * 10.0) / 10.0;
        return new SellerRatingSummary(sellerId, rounded, reviews.size());
    }
}
