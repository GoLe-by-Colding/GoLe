package com.gole.api.account.domain.exception;

import com.gole.api.common.exception.ConflictException;

/**
 * 요구사항 1.2: 이미 등록된 이메일로 가입 시도.
 */
public class EmailAlreadyRegisteredException extends ConflictException {

    public EmailAlreadyRegisteredException(String ignoredEmail) {
        // 서비스 내부 예외도 AOP 로그에 남으므로 이메일 원문을 메시지에 포함하지 않는다.
        super("EMAIL_ALREADY_REGISTERED", "Registration request cannot create a new account");
    }
}
