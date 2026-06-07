package com.gole.api.account.application.port.in;

/**
 * Inbound port: 회원 가입. (요구사항 1.1, 1.2, 1.3)
 */
public interface RegisterAccountUseCase {

    /** 생성된 계정 식별자를 반환한다(계정은 미인증 상태). */
    String register(RegisterAccountCommand command);

    record RegisterAccountCommand(String email, String rawPassword) {
    }
}
