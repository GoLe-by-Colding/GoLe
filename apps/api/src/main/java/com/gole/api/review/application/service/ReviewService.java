package com.gole.api.review.application.service;

import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.common.exception.NotFoundException;
import com.gole.api.review.application.port.in.GetSellerReviewsUseCase;
import com.gole.api.review.application.port.in.WriteReviewUseCase;
import com.gole.api.review.application.port.out.OrderQueryPort;
import com.gole.api.review.application.port.out.OrderQueryPort.OrderSnapshot;
import com.gole.api.review.application.port.out.ReviewIdGeneratorPort;
import com.gole.api.review.application.port.out.ReviewRepositoryPort;
import com.gole.api.review.domain.exception.DuplicateReviewException;
import com.gole.api.review.domain.model.Review;
import com.gole.api.review.domain.model.SellerRatingSummary;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 후기 유스케이스 구현. inbound port를 구현하고 outbound port에만 의존한다.
 * 작성 자격(주문 완료/구매자 일치/주문당 1회)을 검증한 뒤 후기를 저장한다. (요구사항 R1, R2, R3)
 */
@Service
public class ReviewService implements WriteReviewUseCase, GetSellerReviewsUseCase {

    private final ReviewRepositoryPort reviewRepository;
    private final OrderQueryPort orderQuery;
    private final ReviewIdGeneratorPort idGenerator;
    private final Clock clock;

    public ReviewService(
            ReviewRepositoryPort reviewRepository,
            OrderQueryPort orderQuery,
            ReviewIdGeneratorPort idGenerator,
            Clock clock) {
        this.reviewRepository = reviewRepository;
        this.orderQuery = orderQuery;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    public Review write(WriteReviewCommand command) {
        // R2.1: 주문 존재 확인
        OrderSnapshot order = orderQuery
                .findById(command.orderId())
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found: " + command.orderId()));

        // R2.2: 완료된 주문만 후기 가능
        if (!order.completed()) {
            throw new ConflictException(
                    "REVIEW_ORDER_NOT_COMPLETED", "Reviews can only be written for completed orders");
        }

        // R2.3: 요청자가 주문의 구매자여야 함
        if (!order.buyerId().equals(command.reviewerId())) {
            throw new ForbiddenException("NOT_ORDER_BUYER", "Only the buyer of the order can write a review");
        }

        // R2.4: 주문당 1회
        if (reviewRepository.existsByOrderId(command.orderId())) {
            throw new DuplicateReviewException(command.orderId());
        }

        // R1.4: revieweeId는 주문의 판매자에서 파생
        Review review = Review.write(
                idGenerator.newId(),
                command.orderId(),
                command.reviewerId(),
                order.sellerId(),
                command.rating(),
                command.content(),
                Instant.now(clock));

        return reviewRepository.save(review);
    }

    @Override
    public List<Review> bySeller(String sellerId) {
        return reviewRepository.findByRevieweeIdRecentFirst(sellerId);
    }

    @Override
    public SellerRatingSummary ratingOf(String sellerId) {
        return SellerRatingSummary.of(sellerId, reviewRepository.findByRevieweeIdRecentFirst(sellerId));
    }
}
