package com.gole.api.promotion.domain.model;

/** 홍보 초안 에이전트 실행 한 번의 결과. */
public enum RunOutcome {
    /** 초안을 만들어 검토 요청까지 올렸다. */
    SUBMITTED,
    /** 규칙이나 모델 판단으로 만들지 않았다. */
    SKIPPED,
    /** 오류로 끝났다. */
    FAILED
}
