package com.gole.api.account.application.port.out;

import com.gole.api.account.domain.model.AuthProvider;

/**
 * Outbound port: OAuth2 provider 연동(토큰 교환·프로필 조회·동의 URL). 구현은 어댑터가 담당.
 * (소셜 로그인 스펙 S4, S5)
 */
public interface SocialIdentityProviderPort {

    /** client-id 등 토큰이 설정되어 있는지. (S2) */
    boolean isConfigured(AuthProvider provider);

    /** provider 동의 화면 URL. */
    String authorizeUrl(AuthProvider provider, String redirectUri, String state);

    /** authorization code를 교환해 사용자 프로필을 가져온다. */
    SocialProfile fetchProfile(AuthProvider provider, String code, String redirectUri);

    /**
     * @param provider   제공자
     * @param providerId 제공자 측 고유 사용자 ID
     * @param email      이메일(없을 수 있음 → 서비스에서 거부)
     * @param emailVerified 제공자가 이메일 인증을 확인했는지
     */
    record SocialProfile(AuthProvider provider, String providerId, String email, boolean emailVerified) {}
}
