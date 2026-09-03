package com.gole.api.common.exception;

import java.time.Duration;

/** 호출량 제한을 초과한 요청. 클라이언트가 재시도 시각을 알 수 있도록 남은 시간을 보존한다. */
public class TooManyRequestsException extends DomainException {

    private final Duration retryAfter;

    public TooManyRequestsException(String code, String message, Duration retryAfter) {
        super(code, message);
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
