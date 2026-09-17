package com.gole.api.notification.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.notification.application.service.InterestTagAlimtalkProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class InterestTagAlimtalkConfigurationGuardTest {

    private static final DefaultApplicationArguments NO_ARGS = new DefaultApplicationArguments();

    @Test
    void disabledFeatureAllowsIncompleteConfiguration() {
        assertThatCode(() -> guard(false, "", 0, "production", false).run(NO_ARGS))
                .doesNotThrowAnyException();
    }

    @Test
    void enabledFeatureRejectsBlankTemplateInEveryEnvironment() {
        assertThatThrownBy(() -> guard(true, " ", 3, "local", false).run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GOLE_INTEREST_TAG_ALIMTALK_TEMPLATE_ID");
    }

    @Test
    void enabledFeatureRejectsDisabledCoolsmsInPublicEnvironment() {
        assertThatThrownBy(() ->
                        guard(true, "KA01-approved", 3, " Production ", false).run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COOLSMS_ENABLED=true");
    }

    @Test
    void enabledFeatureRejectsDailyLimitBelowOne() {
        assertThatThrownBy(() -> guard(true, "LOCAL_DUMMY", 0, "local", false).run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GOLE_INTEREST_TAG_ALIMTALK_DAILY_LIMIT");
    }

    private static InterestTagAlimtalkConfigurationGuard guard(
            boolean enabled, String templateId, int dailyLimit, String environment, boolean coolsmsEnabled) {
        InterestTagAlimtalkProperties properties = new InterestTagAlimtalkProperties();
        properties.setEnabled(enabled);
        properties.setTemplateId(templateId);
        properties.setDailyLimitPerAccount(dailyLimit);
        return new InterestTagAlimtalkConfigurationGuard(properties, environment, coolsmsEnabled);
    }
}
