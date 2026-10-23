package com.gole.api.order.domain.model;

/**
 * 주문 상태. (요구사항 13, shipping-and-fees R4)
 * PAYMENT_PENDING → FUNDS_HELD → COMPLETED
 *                 → PAYMENT_FAILED
 *                 → CANCELLED → REFUND_PENDING (취소 후 지연 승인 회수)
 *                 → PAYMENT_REVIEW → FUNDS_HELD | PAYMENT_FAILED
 * FUNDS_HELD → REFUND_PENDING → REFUNDED
 * FUNDS_HELD → DISPUTED → COMPLETED | REFUNDED   (분쟁 — 운영자 판정, R4.4)
 */
public enum OrderStatus {
    PAYMENT_PENDING,
    PAYMENT_REVIEW,
    PAYMENT_FAILED,
    CANCELLED,
    FUNDS_HELD,
    DISPUTED,
    COMPLETED,
    REFUND_PENDING,
    REFUNDED;

    /**
     * 더 이상 바뀌지 않는 종결 상태(결제 실패·완료·환불 완료).
     * 취소는 지연 승인·환불 가능성이 남아 있으므로 문의 증거 파기를 허용하는 종결 상태에 포함하지 않는다.
     */
    public boolean isTerminal() {
        return this == PAYMENT_FAILED || this == COMPLETED || this == REFUNDED;
    }
}
