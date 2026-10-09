package com.gole.api.promotion.domain.model;

/**
 * {@link FirstReviewVerdict#HOLD} 판정의 세부 사유. 확정 결함과 증거 부족은 중대한 결함률/
 * 증거 부족률로 별도 집계한다(promotion-review/eval.md 집계 지표).
 */
public enum HoldReasonKind {
    CONFIRMED_DEFECT,
    EVIDENCE_GAP
}
