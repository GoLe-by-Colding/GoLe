package com.gole.api.order.application.port.out;

import com.gole.api.order.domain.model.Order;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port: 주문 영속화. 낙관적 락(@Version)으로 단일 낙찰/멱등 전이를 보장한다.
 * (요구사항 13)
 */
public interface OrderRepositoryPort {

    Order save(Order order);

    Optional<Order> findById(String orderId);

    List<Order> findByBuyerId(String buyerId);

    List<Order> findBySellerId(String sellerId);

    /** 오래된 결제 대기 주문을 생성 시각 순으로 제한 조회한다. */
    default List<Order> findPaymentPendingCreatedBefore(Instant cutoff) {
        return List.of();
    }
}
