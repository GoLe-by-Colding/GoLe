package com.gole.api.order.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * 주문 Spring Data MongoDB 리포지토리. 단순 조회는 파생 쿼리로 처리한다.
 */
public interface OrderMongoRepository extends MongoRepository<OrderDocument, String> {

    /** 구매자별 주문. */
    List<OrderDocument> findTop100ByBuyerIdOrderByCreatedAtDesc(String buyerId);

    /** 판매자별 주문. */
    List<OrderDocument> findTop100BySellerIdOrderByCreatedAtDesc(String sellerId);

    /** 자동 재조정 한 번에 최대 100건만 가져와 PG와 DB에 부하가 몰리지 않게 한다. */
    List<OrderDocument> findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(String status, Instant cutoff);
}
