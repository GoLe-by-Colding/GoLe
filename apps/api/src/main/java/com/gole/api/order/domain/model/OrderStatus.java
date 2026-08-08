package com.gole.api.order.domain.model;

/**
 * 주문 상태. (요구사항 13)
 * PAYMENT_PENDING → FUNDS_HELD → COMPLETED
 *                 → PAYMENT_FAILED
 *                 → PAYMENT_REVIEW → FUNDS_HELD | PAYMENT_FAILED
 * FUNDS_HELD → REFUND_PENDING → REFUNDED
 */
public enum OrderStatus {
    PAYMENT_PENDING,
    PAYMENT_REVIEW,
    PAYMENT_FAILED,
    FUNDS_HELD,
    COMPLETED,
    REFUND_PENDING,
    REFUNDED
}
