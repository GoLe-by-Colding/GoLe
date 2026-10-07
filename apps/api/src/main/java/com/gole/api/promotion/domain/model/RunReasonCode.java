package com.gole.api.promotion.domain.model;

/**
 * 실행 결과의 사유 분류. 공개 Actions 로그에는 이 코드만 나가고, 모델이 쓴 사유 원문은 원장의
 * {@code detail} 에만 남는다(promotion-review D23).
 */
public enum RunReasonCode {
    SUBMITTED,
    /** 검토 대기 초안이 한도(5건)에 찼다. */
    QUEUE_FULL,
    /** 웹 화면 변경이 없는 릴리스다. */
    NO_WEB_CHANGE,
    /** 같은 릴리스로 이미 초안이 있다. */
    ALREADY_DRAFTED,
    /** 모델이 홍보할 것이 없다고 판단했다. */
    MODEL_SKIPPED,
    /** 찍은 화면이 없다. */
    NO_CAPTURES,
    /** 모델 출력이 계약(스키마·번호·길이)을 어겼다. */
    CHOICE_INVALID,
    /** 게이트웨이 호출이 실패했다. */
    GATEWAY_FAILED,
    /** 예상하지 못한 예외로 끝났다. */
    ERROR
}
