package com.gole.api.order.domain.model;

/**
 * 주문 상태. (요구사항 13, shipping-and-fees R4)
 * PAYMENT_PENDING → FUNDS_HELD → COMPLETED
 *                 → PAYMENT_FAILED
 *                 → PAYMENT_REVIEW → FUNDS_HELD | PAYMENT_FAILED
 * FUNDS_HELD → REFUND_PENDING → REFUNDED
 * FUNDS_HELD → DISPUTED → COMPLETED | REFUNDED   (분쟁 — 운영자 판정, R4.4)
 */
public enum OrderStatus {
    PAYMENT_PENDING,
    PAYMENT_REVIEW,
    PAYMENT_FAILED,
    FUNDS_HELD,
    DISPUTED,
    COMPLETED,
    REFUND_PENDING,
    REFUNDED;

    /** 더 이상 바뀌지 않는 종결 상태(결제 실패·완료·환불 완료). 나머지는 돈이나 분쟁 증거가 아직 움직이는 중이다. */
    public boolean isTerminal() {
        return this == PAYMENT_FAILED || this == COMPLETED || this == REFUNDED;
    }
}
