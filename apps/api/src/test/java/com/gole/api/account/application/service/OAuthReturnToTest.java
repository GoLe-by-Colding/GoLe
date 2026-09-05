package com.gole.api.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class OAuthReturnToTest {

    @Test
    void acceptsAndNormalizesSameOriginRelativePaths() {
        assertThat(OAuthReturnTo.sanitize("/collection")).isEqualTo("/collection");
        assertThat(OAuthReturnTo.sanitize("/prices?set=10307#chart")).isEqualTo("/prices?set=10307#chart");
        assertThat(OAuthReturnTo.sanitize("/community/브릭-이야기")).isEqualTo("/community/브릭-이야기");
    }

    @Test
    void rejectsExternalUnsafeAndAuthenticationLoopTargets() {
        List<String> rejected = List.of(
                "https://evil.test/steal",
                "//evil.test/steal",
                "/\\evil.test/steal",
                "/login",
                "/signup?returnTo=/collection",
                "/auth/callback/google",
                "/onboarding",
                "/safe/../login",
                "/safe/%2e%2e/login",
                "/safe\npath");

        assertThat(rejected)
                .allSatisfy(target -> assertThat(OAuthReturnTo.sanitize(target)).isNull());
    }
}
