package com.gole.api.review.adapter.out.persistence;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * 후기 Spring Data MongoDB 리포지토리.
 */
public interface ReviewMongoRepository extends MongoRepository<ReviewDocument, String> {

    /** 특정 판매자에 대한 후기를 최신→오래된 순으로 조회한다. */
    List<ReviewDocument> findTop100ByRevieweeIdAndHiddenAtIsNullOrderByCreatedAtDesc(String revieweeId);

    /** 해당 주문에 후기가 이미 존재하는지 여부. */
    boolean existsByOrderId(String orderId);
}
