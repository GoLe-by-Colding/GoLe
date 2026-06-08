package com.gole.api.account.application.port.out;

/**
 * 이메일 인증 코드 생성 outbound port. (요구사항 1.1)
 */
public interface VerificationCodeGeneratorPort {

    String generateCode();
}
