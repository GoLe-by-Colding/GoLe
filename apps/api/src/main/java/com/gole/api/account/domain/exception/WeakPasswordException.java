package com.gole.api.account.domain.exception;

import com.gole.api.common.exception.DomainException;

/**
 * 요구사항 1.3: 비밀번호가 8자 미만.
 */
public class WeakPasswordException extends DomainException {

    public WeakPasswordException() {
        super("PASSWORD_TOO_SHORT", "Password must be at least 8 characters");
    }
}
