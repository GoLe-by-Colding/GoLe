package com.gole.api.order.domain.model;

/**
 * 판매자 정산 원장의 지급 상태. 전이 규칙은 {@link ManualPayoutPolicy}(수동 지급)에 있다.
 *
 * <p>{@link #PAID}는 기획 용어 SETTLED와 같은, 외부 판매자 지급 확인 상태다.
 */
public enum SettlementStatus {
    PENDING,
    PAYOUT_IN_PROGRESS,
    PAYOUT_FAILED,
    PAYOUT_BLOCKED,
    PAID
}
