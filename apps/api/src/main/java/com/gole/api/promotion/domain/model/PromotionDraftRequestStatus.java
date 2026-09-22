package com.gole.api.promotion.domain.model;

/** 홍보 초안 생성 요청의 수명. 실행 주체는 Python 에이전트이고 Java 는 접수와 결과만 안다. */
public enum PromotionDraftRequestStatus {
    /** 접수됨. 다음 에이전트 실행이 집어간다. */
    PENDING,
    /** 에이전트가 점유 중. lease 가 만료되면 다시 PENDING 으로 집힌다. */
    IN_PROGRESS,
    /** 초안이 만들어졌다. {@code promotionPostId} 가 채워진다. */
    SUCCEEDED,
    /** 에이전트가 실패를 회신했거나 시도 상한을 넘겼다. {@code failureCode} 가 채워진다. */
    FAILED
}
