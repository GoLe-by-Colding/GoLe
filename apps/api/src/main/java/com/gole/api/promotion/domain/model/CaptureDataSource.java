package com.gole.api.promotion.domain.model;

/** 스크린샷이 어떤 데이터 위에서 찍혔나. 검토자가 "실제 모습인가"를 판단하는 근거다. */
public enum CaptureDataSource {
    /** 러너 안 연습용 스택의 데모 데이터. 매물·닉네임·가격이 실제가 아니다. */
    DEMO,
    /** 운영 사이트. */
    PRODUCTION
}
