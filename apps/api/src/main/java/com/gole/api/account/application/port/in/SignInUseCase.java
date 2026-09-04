package com.gole.api.account.application.port.in;

import com.gole.api.account.domain.model.Role;

/**
 * Inbound port: 로그인. (요구사항 1.6, 1.7, 1.8)
 */
public interface SignInUseCase {

    SignInResult signIn(SignInCommand command);

    record SignInCommand(String email, String rawPassword) {}

    /**
     * @param onboardingRequired 로그인 직후 온보딩으로 보내야 하는가. (onboarding R8)
     *     별도 조회 없이 리다이렉트를 결정할 수 있게 로그인 응답에 함께 싣는다.
     */
    record SignInResult(String accountId, String sessionToken, Role role, boolean onboardingRequired) {}
}
