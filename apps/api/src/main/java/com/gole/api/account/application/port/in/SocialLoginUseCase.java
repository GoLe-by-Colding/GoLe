package com.gole.api.account.application.port.in;

import com.gole.api.account.domain.model.AuthProvider;
import com.gole.api.account.domain.model.Role;
import java.util.List;

/**
 * Inbound port: 소셜 로그인(OAuth2). (소셜 로그인 스펙 S3~S6)
 */
public interface SocialLoginUseCase {

    /** 설정(활성)된 provider 목록. (S3) */
    List<AuthProvider> enabledProviders();

    /** provider 동의 화면 URL을 만든다. 서버가 state를 발급·저장한다(CSRF). (S4) */
    String authorizeUrl(AuthProvider provider, String redirectUri);

    /** code를 교환해 프로필을 얻고 find-or-create 후 세션을 발급한다. state 검증 포함. (S5, S6) */
    SocialLoginResult login(SocialLoginCommand command);

    record SocialLoginCommand(AuthProvider provider, String code, String redirectUri, String state) {}

    /**
     * @param onboardingRequired 온보딩으로 보내야 하는가. (onboarding R8, D7)
     *     이번 스코프는 구글 신규가입만 대상이라 카카오·네이버는 항상 {@code false}다.
     */
    record SocialLoginResult(
            String accountId, String sessionToken, Role role, boolean newAccount, boolean onboardingRequired) {}
}
