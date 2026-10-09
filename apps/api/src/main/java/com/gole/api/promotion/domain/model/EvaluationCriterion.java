package com.gole.api.promotion.domain.model;

/**
 * 홍보 초안 품질 루브릭 항목. 각 항목은 0(사용 곤란)~2(그대로 사용 가능)점으로 채점한다 —
 * 사실·근거 / 글·화면 일치 / 독자 가치 / 페르소나·자연스러움 / 구체성·다양성
 * (promotion-review/eval.md 품질 루브릭).
 */
public enum EvaluationCriterion {
    FACT_BASIS,
    SCREEN_MATCH,
    READER_VALUE,
    PERSONA_NATURALNESS,
    SPECIFICITY_VARIETY
}
