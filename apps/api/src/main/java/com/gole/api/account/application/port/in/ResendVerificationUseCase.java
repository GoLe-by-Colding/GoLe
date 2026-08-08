package com.gole.api.account.application.port.in;

/** 만료되거나 전달되지 않은 이메일 인증 코드를 다시 발급한다. */
public interface ResendVerificationUseCase {

    void resend(ResendVerificationCommand command);

    record ResendVerificationCommand(String email) {}
}
