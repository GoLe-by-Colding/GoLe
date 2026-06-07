package com.gole.api.common.exception;

/**
 * 인증 실패 도메인 예외 (HTTP 401로 매핑).
 */
public class UnauthorizedException extends DomainException {

    public UnauthorizedException(String code, String message) {
        super(code, message);
    }
}
