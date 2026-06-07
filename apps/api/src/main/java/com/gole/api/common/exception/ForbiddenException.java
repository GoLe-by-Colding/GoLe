package com.gole.api.common.exception;

/**
 * 권한 없음 도메인 예외 (HTTP 403으로 매핑).
 */
public class ForbiddenException extends DomainException {

    public ForbiddenException(String code, String message) {
        super(code, message);
    }
}
