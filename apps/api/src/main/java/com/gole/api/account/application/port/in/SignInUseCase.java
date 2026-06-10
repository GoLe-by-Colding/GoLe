package com.gole.api.account.application.port.in;

import com.gole.api.account.domain.model.Role;

/**
 * Inbound port: 로그인. (요구사항 1.6, 1.7, 1.8)
 */
public interface SignInUseCase {

    SignInResult signIn(SignInCommand command);

    record SignInCommand(String email, String rawPassword) {}

    record SignInResult(String accountId, String sessionToken, Role role) {}
}
