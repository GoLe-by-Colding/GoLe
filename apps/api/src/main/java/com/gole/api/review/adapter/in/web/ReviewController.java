package com.gole.api.review.adapter.in.web;

import com.gole.api.account.adapter.in.web.AuthenticatedUser;
import com.gole.api.review.adapter.in.web.ReviewDtos.ReviewResponse;
import com.gole.api.review.adapter.in.web.ReviewDtos.SellerRatingResponse;
import com.gole.api.review.adapter.in.web.ReviewDtos.WriteReviewRequest;
import com.gole.api.review.application.port.in.GetSellerReviewsUseCase;
import com.gole.api.review.application.port.in.WriteReviewUseCase;
import com.gole.api.review.application.port.in.WriteReviewUseCase.WriteReviewCommand;
import com.gole.api.review.domain.model.Review;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound 어댑터(REST): 거래 후기 작성 및 셀러 후기/평점 조회. (요구사항 R1, R3, R4)
 */
@Tag(name = "Review", description = "거래 후기 작성·셀러 후기·평점 조회")
@RestController
@RequestMapping("/api/v1")
public class ReviewController {

    private final WriteReviewUseCase writeReviewUseCase;
    private final GetSellerReviewsUseCase getSellerReviewsUseCase;

    public ReviewController(WriteReviewUseCase writeReviewUseCase, GetSellerReviewsUseCase getSellerReviewsUseCase) {
        this.writeReviewUseCase = writeReviewUseCase;
        this.getSellerReviewsUseCase = getSellerReviewsUseCase;
    }

    @PostMapping("/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse write(@Valid @RequestBody WriteReviewRequest request, HttpServletRequest http) {
        Review review = writeReviewUseCase.write(new WriteReviewCommand(
                request.orderId(), AuthenticatedUser.id(http), request.rating(), request.content()));
        return ReviewResponse.from(review);
    }

    @GetMapping("/sellers/{sellerId}/reviews")
    public List<ReviewResponse> bySeller(@PathVariable String sellerId) {
        return getSellerReviewsUseCase.bySeller(sellerId).stream()
                .map(ReviewResponse::from)
                .toList();
    }

    @GetMapping("/sellers/{sellerId}/rating")
    public SellerRatingResponse rating(@PathVariable String sellerId) {
        return SellerRatingResponse.from(getSellerReviewsUseCase.ratingOf(sellerId));
    }
}
