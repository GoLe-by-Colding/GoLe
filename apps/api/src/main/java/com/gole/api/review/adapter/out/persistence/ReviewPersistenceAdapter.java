package com.gole.api.review.adapter.out.persistence;

import com.gole.api.review.application.port.out.ReviewRepositoryPort;
import com.gole.api.review.domain.exception.DuplicateReviewException;
import com.gole.api.review.domain.model.Review;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * 후기 영속성 어댑터. 도메인 {@link Review}와 {@link ReviewDocument}를 양방향 매핑한다.
 */
@Component
public class ReviewPersistenceAdapter implements ReviewRepositoryPort {

    private final ReviewMongoRepository repository;

    public ReviewPersistenceAdapter(ReviewMongoRepository repository) {
        this.repository = repository;
    }

    @Override
    public Review save(Review review) {
        try {
            ReviewDocument saved = repository.save(toDocument(review));
            return toDomain(saved);
        } catch (DuplicateKeyException ex) {
            throw new DuplicateReviewException(review.getOrderId());
        }
    }

    @Override
    public boolean existsByOrderId(String orderId) {
        return repository.existsByOrderId(orderId);
    }

    @Override
    public List<Review> findByRevieweeIdRecentFirst(String revieweeId) {
        return repository.findByRevieweeIdOrderByCreatedAtDesc(revieweeId).stream()
                .map(this::toDomain)
                .toList();
    }

    private ReviewDocument toDocument(Review review) {
        return new ReviewDocument(
                review.getId(),
                review.getOrderId(),
                review.getReviewerId(),
                review.getRevieweeId(),
                review.getRating(),
                review.getContent(),
                review.getCreatedAt());
    }

    private Review toDomain(ReviewDocument document) {
        return new Review(
                document.getId(),
                document.getOrderId(),
                document.getReviewerId(),
                document.getRevieweeId(),
                document.getRating(),
                document.getContent(),
                document.getCreatedAt());
    }
}
