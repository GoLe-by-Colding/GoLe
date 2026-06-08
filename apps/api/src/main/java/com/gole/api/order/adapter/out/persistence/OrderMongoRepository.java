package com.gole.api.order.adapter.out.persistence;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * 주문 Spring Data MongoDB 리포지토리. 단순 조회는 파생 쿼리로 처리한다.
 */
public interface OrderMongoRepository extends MongoRepository<OrderDocument, String> {

    /** 구매자별 주문. */
    List<OrderDocument> findByBuyerId(String buyerId);

    /** 판매자별 주문. */
    List<OrderDocument> findBySellerId(String sellerId);
}
