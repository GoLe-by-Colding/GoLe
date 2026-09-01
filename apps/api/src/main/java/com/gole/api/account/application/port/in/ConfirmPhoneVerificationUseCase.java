package com.gole.api.account.application.port.in;

/**
 * Inbound port: 전화번호 인증 코드 확인. (onboarding R5, D2)
 */
public interface ConfirmPhoneVerificationUseCase {

    /** 성공 시 {@code phoneVerifiedAt}을 즉시 영속화한다. 5회 오답이면 해당 OTP를 무효화한다. */
    void confirm(ConfirmPhoneVerificationCommand command);

    record ConfirmPhoneVerificationCommand(String accountId, String code) {}
}
