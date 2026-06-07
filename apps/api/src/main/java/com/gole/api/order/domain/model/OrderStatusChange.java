package com.gole.api.order.domain.model;

import java.time.Instant;

/**
 * 주문 상태 전이 이력 항목. (요구사항 13.8: 감사 가능성)
 */
public record OrderStatusChange(OrderStatus status, Instant occurredAt) {
}
