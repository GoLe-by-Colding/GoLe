package com.gole.api.promotion.domain.model;

/**
 * 첫 검토 판정의 이유 태그 — 사실 오류 / 증거 부족 / 화면 부적합 / 정보 노출 / 홍보 가치
 * 부족 / 말투 / 반복 / 형식 / 기타(promotion-review/eval.md 첫 검토 판정).
 */
public enum EvaluationReasonTag {
    FACTUAL_ERROR,
    EVIDENCE_GAP,
    SCREEN_MISMATCH,
    INFO_EXPOSURE,
    LOW_PROMO_VALUE,
    TONE,
    REPETITION,
    FORMAT,
    OTHER
}
