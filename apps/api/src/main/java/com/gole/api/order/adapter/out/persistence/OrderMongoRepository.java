package com.gole.api.order.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * 주문 Spring Data MongoDB 리포지토리. 단순 조회는 파생 쿼리로 처리한다.
 */
public interface OrderMongoRepository extends MongoRepository<OrderDocument, String> {

    /** 구매자별 주문. */
    List<OrderDocument> findByBuyerId(String buyerId);

    /** 판매자별 주문. */
    List<OrderDocument> findBySellerId(String sellerId);

    /**
     * PG 결제 식별자로 주문을 찾는다. 웹훅은 결제 식별자만 들고 오므로 이 조회가 유일한 통로다.
     * 과거 시도의 식별자로도 찾히도록 배열 원소를 대상으로 한다.
     */
    Optional<OrderDocument> findFirstByPaymentIdsContains(String paymentId);
}
