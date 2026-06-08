package com.gole.api.common.exception;

/**
 * 잘못된 요청(입력 검증 실패 등)일 때의 도메인 예외 (HTTP 400으로 매핑).
 */
public class BadRequestException extends DomainException {

    public BadRequestException(String code, String message) {
        super(code, message);
    }
}
