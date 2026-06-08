package com.gole.api.review.application.port.out;

import com.gole.api.review.domain.model.Review;
import java.util.List;

/**
 * 후기 영속성 outbound port. 애플리케이션은 저장 기술(MongoDB 등)에 의존하지 않는다.
 */
public interface ReviewRepositoryPort {

    /** 후기를 저장하고 영속된 결과를 반환한다. */
    Review save(Review review);

    /** 해당 주문에 이미 후기가 존재하는지 여부. (요구사항 R2.4) */
    boolean existsByOrderId(String orderId);

    /** 특정 판매자에 대한 후기를 최신→오래된 순으로 조회한다. (요구사항 R3.1) */
    List<Review> findByRevieweeIdRecentFirst(String revieweeId);
}
