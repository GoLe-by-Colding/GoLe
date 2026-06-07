package com.gole.api.account.domain.exception;

import com.gole.api.common.exception.DomainException;

/**
 * 요구사항 1.5: 인증 코드 만료/불일치 등 인증 실패.
 */
public class VerificationException extends DomainException {

    public VerificationException(String code, String message) {
        super(code, message);
    }
}
