package com.gole.api.order.application.port.out;

import com.gole.api.order.domain.model.Order;
import com.gole.api.order.domain.model.OrderStatus;
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

    /** 마지막 전이가 cutoff 이전인 특정 상태 주문(파이프라인 만료 후보, R9). */
    default List<Order> findByStatusChangedBefore(OrderStatus status, Instant cutoff) {
        return List.of();
    }

    /** 특정 상태의 주문(예외 큐 조회용). */
    default List<Order> findByStatus(OrderStatus status) {
        return List.of();
    }

    /**
     * 특정 상태 주문을 ID 커서 다음부터 제한 조회한다.
     *
     * <p>주기 작업이 매번 같은 오래된 100건에 막히지 않고 전체 예외 큐를 순환하기 위한 포트다.
     * 구현하지 않은 테스트 대역은 기존 상태 조회 결과를 정렬해 호환한다.
     */
    default List<Order> findByStatusAfterId(OrderStatus status, String afterId) {
        return findByStatus(status).stream()
                .filter(order -> afterId == null || order.getId().compareTo(afterId) > 0)
                .sorted(java.util.Comparator.comparing(Order::getId))
                .limit(100)
                .toList();
    }
}
