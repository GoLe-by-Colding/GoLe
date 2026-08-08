package com.gole.api.account.domain.exception;

import com.gole.api.common.exception.DomainException;

/** BCrypt가 안전하게 처리하는 UTF-8 72바이트를 넘는 비밀번호를 거부한다. */
public class PasswordTooLongException extends DomainException {

    public PasswordTooLongException() {
        super("PASSWORD_TOO_LONG", "Password must be at most 72 UTF-8 bytes");
    }
}
