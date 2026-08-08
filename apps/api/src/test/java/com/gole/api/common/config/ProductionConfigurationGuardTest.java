package com.gole.api.common.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class ProductionConfigurationGuardTest {

    private static final DefaultApplicationArguments NO_ARGS = new DefaultApplicationArguments();

    @Test
    void productionRejectsLoggingVerificationCodeAdapter() {
        ProductionConfigurationGuard guard = guard("production", false, false);

        assertThatThrownBy(() -> guard.run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("verification email");
    }

    @Test
    void productionRejectsAnySampleSeed() {
        ProductionConfigurationGuard guard = guard("prod", true, true);

        assertThatThrownBy(() -> guard.run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sample data seeds")
                .hasMessageContaining("catalog");
    }

    @Test
    void localDefaultsAndHardenedProductionAreAllowed() {
        assertThatCode(() -> guard("local", false, true).run(NO_ARGS)).doesNotThrowAnyException();
        assertThatCode(() -> guard("production", true, false).run(NO_ARGS)).doesNotThrowAnyException();
    }

    private static ProductionConfigurationGuard guard(String environment, boolean email, boolean seeds) {
        return new ProductionConfigurationGuard(environment, email, seeds, seeds, seeds, seeds, seeds, seeds);
    }
}
