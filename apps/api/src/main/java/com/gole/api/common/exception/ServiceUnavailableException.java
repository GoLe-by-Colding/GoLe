package com.gole.api.common.exception;

/** 계획된 운영 준비 미완료처럼 재시도 가능한 서비스 비가용 상태(HTTP 503). */
public class ServiceUnavailableException extends DomainException {

    public ServiceUnavailableException(String code, String message) {
        super(code, message);
    }
}
