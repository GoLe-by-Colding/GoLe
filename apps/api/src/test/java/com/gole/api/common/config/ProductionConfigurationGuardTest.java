package com.gole.api.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.OrderUtils;

class ProductionConfigurationGuardTest {

    private static final DefaultApplicationArguments NO_ARGS = new DefaultApplicationArguments();

    @Test
    void productionAllowsHardenedConfigurationWhenSeedsAreClosed() {
        assertThatCode(() -> guard("production", false).run(NO_ARGS)).doesNotThrowAnyException();
    }

    @Test
    void stagingAllowsHardenedConfigurationWhenSeedsAreClosed() {
        assertThatCode(() -> guard("staging", false).run(NO_ARGS)).doesNotThrowAnyException();
    }

    @Test
    void unknownOrBlankEnvironmentFailsClosedInsteadOfEnablingDevelopmentSeeds() {
        for (String environment : new String[] {"production-typo", "", "preview"}) {
            assertThatThrownBy(() -> guard(environment, true).run(NO_ARGS))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("sample data seeds");
        }
    }

    @Test
    void publicEnvironmentNormalizationCannotBeBypassedWithWhitespaceOrCase() {
        assertThatThrownBy(() -> guard(" Production ", true).run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sample data seeds");
    }

    @Test
    void productionRejectsAnySampleSeed() {
        ProductionConfigurationGuard guard = guard("prod", true);

        assertThatThrownBy(() -> guard.run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sample data seeds")
                .hasMessageContaining("catalog");
    }

    @Test
    void stagingAlsoRejectsAnySampleSeed() {
        ProductionConfigurationGuard guard = guard("staging", true);

        assertThatThrownBy(() -> guard.run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sample data seeds");
    }

    @Test
    void guardRunsBeforeEverySeederCanMutateData() {
        assertThat(OrderUtils.getOrder(ProductionConfigurationGuard.class, Ordered.LOWEST_PRECEDENCE))
                .isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }

    @Test
    void stagingRejectsDemoOrUnverifiedPricingEvidence() {
        ProductionConfigurationGuard guard = new ProductionConfigurationGuard(
                "staging", false, false, false, false, false, false, false, true, false);

        assertThatThrownBy(() -> guard.run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unverified legacy pricing evidence");
    }

    @Test
    void localDefaultsAndHardenedProductionAreAllowed() {
        assertThatCode(() -> guard("local", true).run(NO_ARGS)).doesNotThrowAnyException();
        assertThatCode(() -> guard("production", false).run(NO_ARGS)).doesNotThrowAnyException();
    }

    private static ProductionConfigurationGuard guard(String environment, boolean seeds) {
        return new ProductionConfigurationGuard(
                environment, seeds, seeds, seeds, seeds, seeds, seeds, seeds, false, false);
    }
}
