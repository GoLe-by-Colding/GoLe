package com.gole.api.order.adapter.out.payment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class PaymentConfigurationGuardTest {

    private static final DefaultApplicationArguments NO_ARGS = new DefaultApplicationArguments();

    @Test
    void productionRejectsStubGateway() {
        PaymentConfigurationGuard guard = new PaymentConfigurationGuard("production", false, "");

        assertThatThrownBy(() -> guard.run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stub payment gateway");
    }

    @Test
    void enabledPortOneRequiresSecret() {
        PaymentConfigurationGuard guard = new PaymentConfigurationGuard("local", true, " ");

        assertThatThrownBy(() -> guard.run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PORTONE_API_SECRET");
    }

    @Test
    void localStubAndConfiguredProductionAreAllowed() {
        assertThatCode(() -> new PaymentConfigurationGuard("local", false, "").run(NO_ARGS))
                .doesNotThrowAnyException();
        assertThatCode(() -> new PaymentConfigurationGuard("prod", true, "secret").run(NO_ARGS))
                .doesNotThrowAnyException();
    }
}
