package com.gole.api.notification.application.port.out;

import java.util.Objects;

/** 알림톡 요청이 CoolSMS에 정상 접수되지 못했을 때의 공급자 독립 예외. */
public class AlimtalkSendException extends RuntimeException {

    private final FailureType failureType;

    public AlimtalkSendException(FailureType failureType, String message) {
        super(message);
        this.failureType = Objects.requireNonNull(failureType, "failureType");
    }

    public AlimtalkSendException(FailureType failureType, String message, Throwable cause) {
        super(message, cause);
        this.failureType = Objects.requireNonNull(failureType, "failureType");
    }

    public FailureType getFailureType() {
        return failureType;
    }

    /** 공급자가 명시적으로 미접수를 응답해 동일 명령 재시도가 안전한 경우에만 true다. */
    public boolean isRetrySafe() {
        return failureType == FailureType.RATE_LIMITED;
    }

    public enum FailureType {
        INVALID_REQUEST,
        AUTHENTICATION,
        RATE_LIMITED,
        PROVIDER_REJECTED,
        ACCEPTANCE_UNKNOWN,
        PROVIDER_FAILURE
    }
}
