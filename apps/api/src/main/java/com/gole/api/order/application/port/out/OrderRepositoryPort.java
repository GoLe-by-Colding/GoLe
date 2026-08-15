package com.gole.api.order.application.port.out;

import com.gole.api.order.domain.model.Order;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port: 주문 영속화. 낙관적 락(@Version)으로 단일 낙찰/멱등 전이를 보장한다.
 * (요구사항 13)
 */
public interface OrderRepositoryPort {

    Order save(Order order);

    Optional<Order> findById(String orderId);

    /**
     * PG 결제 식별자로 주문을 찾는다. 웹훅은 주문 id를 모르고 결제 식별자만 들고 온다.
     *
     * <p>결제 시도 도입 이전 주문은 주문 id가 곧 결제 식별자였으므로 그 경우도 찾혀야 한다.
     */
    Optional<Order> findByPaymentId(String paymentId);

    List<Order> findByBuyerId(String buyerId);

    List<Order> findBySellerId(String sellerId);
}
