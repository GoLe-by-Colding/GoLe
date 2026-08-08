package com.gole.api.account.adapter.out.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.account.domain.model.AuthProvider;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RestClientSocialIdentityProviderAdapterTest {

    @Test
    void googleProfile_usesEmailVerifiedClaim() {
        var verified = RestClientSocialIdentityProviderAdapter.parseProfile(
                AuthProvider.GOOGLE, Map.of("sub", "g-1", "email", "user@example.com", "email_verified", true));
        var unverified = RestClientSocialIdentityProviderAdapter.parseProfile(
                AuthProvider.GOOGLE, Map.of("sub", "g-2", "email", "other@example.com", "email_verified", false));

        assertThat(verified.emailVerified()).isTrue();
        assertThat(unverified.emailVerified()).isFalse();
    }

    @Test
    void kakaoProfile_requiresBothValidAndVerifiedEmail() {
        var valid = RestClientSocialIdentityProviderAdapter.parseProfile(
                AuthProvider.KAKAO,
                Map.of(
                        "id",
                        42,
                        "kakao_account",
                        Map.of("email", "user@example.com", "is_email_valid", true, "is_email_verified", true)));
        var expired = RestClientSocialIdentityProviderAdapter.parseProfile(
                AuthProvider.KAKAO,
                Map.of(
                        "id",
                        43,
                        "kakao_account",
                        Map.of("email", "old@example.com", "is_email_valid", false, "is_email_verified", true)));

        assertThat(valid.emailVerified()).isTrue();
        assertThat(expired.emailVerified()).isFalse();
    }
}
