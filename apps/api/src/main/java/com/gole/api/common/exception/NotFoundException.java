package com.gole.api.common.exception;

/**
 * 요청한 리소스를 찾을 수 없을 때의 도메인 예외 (HTTP 404로 매핑).
 */
public class NotFoundException extends DomainException {

    public NotFoundException(String code, String message) {
        super(code, message);
    }
}
