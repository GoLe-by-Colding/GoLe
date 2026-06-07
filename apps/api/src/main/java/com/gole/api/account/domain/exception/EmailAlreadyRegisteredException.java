package com.gole.api.account.domain.exception;

import com.gole.api.common.exception.ConflictException;

/**
 * 요구사항 1.2: 이미 등록된 이메일로 가입 시도.
 */
public class EmailAlreadyRegisteredException extends ConflictException {

    public EmailAlreadyRegisteredException(String email) {
        super("EMAIL_ALREADY_REGISTERED", "Email is already registered: " + email);
    }
}
