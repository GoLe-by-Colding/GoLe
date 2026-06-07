package com.gole.api.common.exception;

/**
 * 도메인 규칙 위반의 기반 예외. 프레임워크에 의존하지 않는다.
 * 각 bounded context의 구체 예외가 이를 상속한다.
 */
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
