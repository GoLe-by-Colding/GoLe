package com.gole.api.account.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

class OAuthRedirectUriPolicyTest {

    private final OAuthRedirectUriPolicy policy = new OAuthRedirectUriPolicy(String.join(
            ",",
            "https://gole.co.kr/auth/callback/google",
            "https://gole.co.kr/auth/callback/kakao",
            "http://localhost:3010/auth/callback/google"));

    @Test
    void acceptsOnlyAnExactConfiguredCallback() {
        assertThatCode(() -> policy.requireAllowed("https://gole.co.kr/auth/callback/google"))
                .doesNotThrowAnyException();
        // 명시하지 않은 default port 표기는 같은 HTTPS endpoint로 정규화한다.
        assertThatCode(() -> policy.requireAllowed("https://gole.co.kr:443/auth/callback/google"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsForeignOriginsAndUnlistedPathsWithoutReflectingTheValue() {
        String malicious = "https://evil.test/auth/callback/google";

        assertThatThrownBy(() -> policy.requireAllowed(malicious))
                .isInstanceOf(BadRequestException.class)
                .hasMessageNotContaining("evil.test");
        assertThatThrownBy(() -> policy.requireAllowed("https://gole.co.kr/auth/callback/naver"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsQueryFragmentCredentialsAndNonLoopbackHttp() {
        for (String invalid : new String[] {
            "https://gole.co.kr/auth/callback/google?next=/admin",
            "https://gole.co.kr/auth/callback/google#fragment",
            "https://user@gole.co.kr/auth/callback/google",
            "http://gole.co.kr/auth/callback/google"
        }) {
            assertThatThrownBy(() -> policy.requireAllowed(invalid))
                    .as(invalid)
                    .isInstanceOf(BadRequestException.class);
        }
    }

    @Test
    void invalidOrEmptyConfigurationFailsClosedAtStartup() {
        assertThatThrownBy(() -> new OAuthRedirectUriPolicy("")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new OAuthRedirectUriPolicy("https://gole.co.kr/callback, "))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new OAuthRedirectUriPolicy("http://example.com/callback"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void publicEnvironmentAllowsOnlyCanonicalGoleCallbacks() {
        String canonical = String.join(
                ",",
                "https://gole.co.kr/auth/callback/google",
                "https://gole.co.kr/auth/callback/kakao",
                "https://gole.co.kr/auth/callback/naver");

        assertThatCode(() -> new OAuthRedirectUriPolicy(canonical, "production"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> new OAuthRedirectUriPolicy(
                        canonical + ",https://attacker.example/auth/callback/google", "production"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Public OAuth redirect URI");
        assertThatThrownBy(() -> new OAuthRedirectUriPolicy("http://localhost:3000/auth/callback/google", "preview"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Public OAuth redirect URI");
    }
}
