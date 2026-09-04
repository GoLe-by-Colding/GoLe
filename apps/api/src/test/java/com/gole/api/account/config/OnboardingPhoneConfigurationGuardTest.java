package com.gole.api.account.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class OnboardingPhoneConfigurationGuardTest {

    private static final DefaultApplicationArguments NO_ARGS = new DefaultApplicationArguments();

    @Test
    void publicEnvironmentAllowsPhoneToBeOptionalWithoutCoolsms() {
        assertThatCode(() -> guard("production", false, false, "").run(NO_ARGS)).doesNotThrowAnyException();
    }

    @Test
    void publicEnvironmentRejectsRequiredPhoneWhenCoolsmsIsDisabled() {
        assertThatThrownBy(() -> guard("staging", true, false, "").run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COOLSMS_ENABLED=true");
    }

    @Test
    void publicEnvironmentRejectsRequiredPhoneWithoutApprovedTemplate() {
        assertThatThrownBy(() -> guard("production", true, true, " ").run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GOLE_ONBOARDING_PHONE_TEMPLATE_ID");
    }

    @Test
    void publicEnvironmentAllowsFullyConfiguredRequiredPhone() {
        assertThatCode(() -> guard("prod", true, true, "KA01-approved").run(NO_ARGS))
                .doesNotThrowAnyException();
    }

    @Test
    void localEnvironmentKeepsTheExistingLoggingDevelopmentFlow() {
        assertThatCode(() -> guard("local", true, false, "").run(NO_ARGS)).doesNotThrowAnyException();
    }

    private static OnboardingPhoneConfigurationGuard guard(
            String environment, boolean required, boolean coolsmsEnabled, String templateId) {
        return new OnboardingPhoneConfigurationGuard(environment, required, coolsmsEnabled, templateId);
    }
}
