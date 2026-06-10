package com.gole.api.review.adapter.in.web;

import com.gole.api.review.domain.model.Review;
import com.gole.api.review.domain.model.SellerRatingSummary;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class ReviewDtos {

    private ReviewDtos() {}

    public record WriteReviewRequest(
            @NotBlank String orderId,
            @NotBlank String reviewerId,
            @Min(1) @Max(5) int rating,
            @NotBlank @Size(max = 1000) String content) {}

    public record ReviewResponse(
            String id,
            String orderId,
            String reviewerId,
            String revieweeId,
            int rating,
            String content,
            Instant createdAt) {

        public static ReviewResponse from(Review review) {
            return new ReviewResponse(
                    review.getId(),
                    review.getOrderId(),
                    review.getReviewerId(),
                    review.getRevieweeId(),
                    review.getRating(),
                    review.getContent(),
                    review.getCreatedAt());
        }
    }

    public record SellerRatingResponse(String sellerId, double average, long count) {

        public static SellerRatingResponse from(SellerRatingSummary summary) {
            return new SellerRatingResponse(summary.sellerId(), summary.average(), summary.count());
        }
    }
}
