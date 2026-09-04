package com.gole.api.common.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class WebCorsConfigurationGuardTest {

    private static final DefaultApplicationArguments NO_ARGS = new DefaultApplicationArguments();

    @Test
    void productionAllowsOnlyApexAndCanonicalWwwHttpsOrigins() {
        var guard = new WebCorsConfigurationGuard(
                "production", new String[] {"https://www.gole.co.kr", "https://gole.co.kr"});

        assertThatCode(() -> guard.run(NO_ARGS)).doesNotThrowAnyException();
    }

    @Test
    void productionRejectsLocalWildcardHttpAndAdditionalOrigins() {
        for (String[] origins : new String[][] {
            {"http://localhost:3000"},
            {"*"},
            {"http://gole.co.kr", "https://www.gole.co.kr"},
            {"https://gole.co.kr", "https://www.gole.co.kr", "https://attacker.example"}
        }) {
            var guard = new WebCorsConfigurationGuard("production", origins);
            assertThatThrownBy(() -> guard.run(NO_ARGS))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Public CORS origins");
        }
    }

    @Test
    void localDevelopmentKeepsConfigurableOrigins() {
        var guard = new WebCorsConfigurationGuard("local", new String[] {"http://localhost:3010"});

        assertThatCode(() -> guard.run(NO_ARGS)).doesNotThrowAnyException();
    }

    @Test
    void unknownEnvironmentAlsoRequiresPublicOrigins() {
        var guard = new WebCorsConfigurationGuard("production-typo", new String[] {"http://localhost:3000"});

        assertThatThrownBy(() -> guard.run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Public CORS origins");
    }
}
