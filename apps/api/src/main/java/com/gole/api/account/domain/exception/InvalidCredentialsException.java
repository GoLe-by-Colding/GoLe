package com.gole.api.account.domain.exception;

import com.gole.api.common.exception.UnauthorizedException;

/**
 * 요구사항 1.7: 잘못된 이메일/비밀번호 로그인.
 */
public class InvalidCredentialsException extends UnauthorizedException {

    public InvalidCredentialsException() {
        super("INVALID_CREDENTIALS", "Invalid email or password");
    }
}
