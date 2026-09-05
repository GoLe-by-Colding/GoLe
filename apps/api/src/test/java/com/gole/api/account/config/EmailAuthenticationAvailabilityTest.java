package com.gole.api.account.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.common.exception.ServiceUnavailableException;
import org.junit.jupiter.api.Test;

class EmailAuthenticationAvailabilityTest {

    @Test
    void publicEnvironmentWithoutMailFailsClosed() {
        EmailAuthenticationAvailability availability = new EmailAuthenticationAvailability("production", false);

        assertThat(availability.available()).isFalse();
        assertThatThrownBy(availability::requireAvailable)
                .isInstanceOf(ServiceUnavailableException.class)
                .hasFieldOrPropertyWithValue("code", EmailAuthenticationAvailability.UNAVAILABLE_CODE);
    }

    @Test
    void explicitMailDeliveryOrDeveloperEnvironmentProvidesACompletionPath() {
        assertThatCode(() -> new EmailAuthenticationAvailability("production", true).requireAvailable())
                .doesNotThrowAnyException();
        assertThatCode(() -> new EmailAuthenticationAvailability("test", false).requireAvailable())
                .doesNotThrowAnyException();
    }

    @Test
    void unknownEnvironmentDoesNotFallBackToDevelopmentLogging() {
        assertThat(new EmailAuthenticationAvailability("production-typo", false).available())
                .isFalse();
        assertThat(new EmailAuthenticationAvailability("", false).available()).isFalse();
    }
}
