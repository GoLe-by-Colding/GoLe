package com.gole.api.promotion.domain.model;

/**
 * 첫 검토 판정 — 그대로 사용 / 경미한 수정 / 대폭 수정 / 사용 불가 / 보류. 실제 관리자의
 * 승인 상태와 별개이며, 검토 후 재분류하더라도 첫 판정은 보존한다(promotion-review/eval.md
 * 첫 검토 판정).
 */
public enum FirstReviewVerdict {
    USE_AS_IS,
    MINOR_EDIT,
    MAJOR_REWRITE,
    UNUSABLE,
    HOLD
}
