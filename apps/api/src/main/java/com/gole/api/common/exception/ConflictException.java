package com.gole.api.common.exception;

/**
 * 리소스 충돌(중복) 도메인 예외 (HTTP 409로 매핑).
 */
public class ConflictException extends DomainException {

    public ConflictException(String code, String message) {
        super(code, message);
    }
}
