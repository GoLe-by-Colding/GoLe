package com.gole.api.account.domain.exception;

import com.gole.api.common.exception.ForbiddenException;

/** 이메일 인증 전 로그인 시도를 차단한다. */
public class AccountNotVerifiedException extends ForbiddenException {

    public AccountNotVerifiedException() {
        super("ACCOUNT_NOT_VERIFIED", "이메일 인증을 완료해 주세요");
    }
}
