package com.gole.api.account.application.port.in;

/**
 * Inbound port: 이메일 인증 코드 검증. (요구사항 1.4, 1.5)
 */
public interface VerifyEmailUseCase {

    void verify(VerifyEmailCommand command);

    record VerifyEmailCommand(String email, String code) {
    }
}
