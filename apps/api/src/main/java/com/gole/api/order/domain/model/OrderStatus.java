package com.gole.api.order.domain.model;

/**
 * 주문 상태. (요구사항 13)
 * PAYMENT_PENDING → FUNDS_HELD → COMPLETED
 *                 → PAYMENT_FAILED
 * FUNDS_HELD → REFUNDED
 */
public enum OrderStatus {
    PAYMENT_PENDING,
    PAYMENT_FAILED,
    FUNDS_HELD,
    COMPLETED,
    REFUNDED
}
