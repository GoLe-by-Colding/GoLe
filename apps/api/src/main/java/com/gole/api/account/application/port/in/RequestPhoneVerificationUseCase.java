package com.gole.api.account.application.port.in;

/**
 * Inbound port: 전화번호 인증 코드 발송 요청. (onboarding R4, D2~D4)
 */
public interface RequestPhoneVerificationUseCase {

    /** 형식·유일성·쿨다운·일일 한도를 통과하면 OTP를 발송한다. */
    PhoneVerificationRequested request(RequestPhoneVerificationCommand command);

    record RequestPhoneVerificationCommand(String accountId, String phoneNumber) {}

    /**
     * @param maskedPhoneNumber 어디로 보냈는지 화면에 되돌려 주기 위한 마스킹 표기
     * @param expiresInSeconds 코드 유효 시간
     */
    record PhoneVerificationRequested(String maskedPhoneNumber, long expiresInSeconds) {}
}
