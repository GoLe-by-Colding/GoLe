package com.gole.api.review.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.common.exception.NotFoundException;
import com.gole.api.review.application.port.in.WriteReviewUseCase.WriteReviewCommand;
import com.gole.api.review.application.port.out.OrderQueryPort;
import com.gole.api.review.application.port.out.ReviewIdGeneratorPort;
import com.gole.api.review.application.port.out.ReviewRepositoryPort;
import com.gole.api.review.domain.exception.DuplicateReviewException;
import com.gole.api.review.domain.exception.InvalidRatingException;
import com.gole.api.review.domain.model.Review;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReviewServiceTest {

    private InMemoryReviews reviews;
    private InMemoryOrders orders;
    private ReviewService service;

    @BeforeEach
    void setUp() {
        reviews = new InMemoryReviews();
        orders = new InMemoryOrders();
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        service = new ReviewService(reviews, orders, new SeqIds(), clock);
    }

    @Test
    void write_completedOrderByBuyer_persistsReviewForSeller() {
        orders.put("order-1", "buyer-1", "seller-1", true);

        Review review = service.write(new WriteReviewCommand("order-1", "buyer-1", 5, "최고의 거래였어요"));

        assertThat(review.getId()).isEqualTo("id-1");
        assertThat(review.getRevieweeId()).isEqualTo("seller-1"); // R1.4 판매자 파생
        assertThat(review.getRating()).isEqualTo(5);
        assertThat(service.bySeller("seller-1")).hasSize(1);
    }

    @Test
    void write_unknownOrder_isNotFound() {
        assertThatThrownBy(() -> service.write(new WriteReviewCommand("nope", "buyer-1", 5, "내용")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void write_orderNotCompleted_isConflict() {
        orders.put("order-1", "buyer-1", "seller-1", false);

        assertThatThrownBy(() -> service.write(new WriteReviewCommand("order-1", "buyer-1", 5, "내용")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void write_byNonBuyer_isForbidden() {
        orders.put("order-1", "buyer-1", "seller-1", true);

        assertThatThrownBy(() -> service.write(new WriteReviewCommand("order-1", "intruder", 5, "내용")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void write_twiceForSameOrder_isDuplicate() {
        orders.put("order-1", "buyer-1", "seller-1", true);
        service.write(new WriteReviewCommand("order-1", "buyer-1", 5, "첫 후기"));

        assertThatThrownBy(() -> service.write(new WriteReviewCommand("order-1", "buyer-1", 4, "또 후기")))
                .isInstanceOf(DuplicateReviewException.class);
    }

    @Test
    void write_ratingOutOfRange_isRejected() {
        orders.put("order-1", "buyer-1", "seller-1", true);

        assertThatThrownBy(() -> service.write(new WriteReviewCommand("order-1", "buyer-1", 6, "내용")))
                .isInstanceOf(InvalidRatingException.class);
    }

    @Test
    void ratingOf_averagesAndCounts_roundedToOneDecimal() {
        orders.put("order-1", "buyer-1", "seller-1", true);
        orders.put("order-2", "buyer-2", "seller-1", true);
        orders.put("order-3", "buyer-3", "seller-1", true);
        service.write(new WriteReviewCommand("order-1", "buyer-1", 5, "굿"));
        service.write(new WriteReviewCommand("order-2", "buyer-2", 4, "괜찮음"));
        service.write(new WriteReviewCommand("order-3", "buyer-3", 4, "보통"));

        var summary = service.ratingOf("seller-1");

        assertThat(summary.count()).isEqualTo(3);
        assertThat(summary.average()).isEqualTo(4.3); // (5+4+4)/3 = 4.333 → 4.3
    }

    @Test
    void ratingOf_noReviews_isZero() {
        var summary = service.ratingOf("seller-x");

        assertThat(summary.count()).isZero();
        assertThat(summary.average()).isZero();
    }

    private static final class InMemoryReviews implements ReviewRepositoryPort {
        private final List<Review> store = new ArrayList<>();

        @Override
        public Review save(Review review) {
            store.removeIf(r -> r.getId().equals(review.getId()));
            store.add(review);
            return review;
        }

        @Override
        public boolean existsByOrderId(String orderId) {
            return store.stream().anyMatch(r -> r.getOrderId().equals(orderId));
        }

        @Override
        public List<Review> findByRevieweeIdRecentFirst(String revieweeId) {
            return store.stream()
                    .filter(r -> r.getRevieweeId().equals(revieweeId))
                    .sorted(Comparator.comparing(Review::getCreatedAt).reversed())
                    .toList();
        }
    }

    private static final class InMemoryOrders implements OrderQueryPort {
        private final Map<String, OrderSnapshot> store = new HashMap<>();

        void put(String orderId, String buyerId, String sellerId, boolean completed) {
            store.put(orderId, new OrderSnapshot(orderId, buyerId, sellerId, completed));
        }

        @Override
        public Optional<OrderSnapshot> findById(String orderId) {
            return Optional.ofNullable(store.get(orderId));
        }
    }

    private static final class SeqIds implements ReviewIdGeneratorPort {
        private final AtomicInteger n = new AtomicInteger();

        @Override
        public String newId() {
            return "id-" + n.incrementAndGet();
        }
    }
}
