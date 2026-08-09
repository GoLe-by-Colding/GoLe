package com.gole.api.order.adapter.out.payment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class PaymentConfigurationGuardTest {

    private static final DefaultApplicationArguments NO_ARGS = new DefaultApplicationArguments();

    @Test
    void productionRejectsStubGateway() {
        PaymentConfigurationGuard guard = new PaymentConfigurationGuard("production", false, "", "", "", "", "TEST");

        assertThatThrownBy(() -> guard.run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stub payment gateway");
    }

    @Test
    void enabledPortOneRequiresSecret() {
        PaymentConfigurationGuard guard =
                new PaymentConfigurationGuard("local", true, " ", "webhook-secret", "store-1", "channel-1", "TEST");

        assertThatThrownBy(() -> guard.run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PORTONE_API_SECRET");
    }

    @Test
    void enabledPortOneRequiresWebhookSecret() {
        PaymentConfigurationGuard guard =
                new PaymentConfigurationGuard("local", true, "api-secret", " ", "store-1", "channel-1", "TEST");

        assertThatThrownBy(() -> guard.run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PORTONE_WEBHOOK_SECRET");
    }

    @Test
    void enabledPortOneRequiresStoreId() {
        PaymentConfigurationGuard guard =
                new PaymentConfigurationGuard("local", true, "api-secret", "webhook-secret", " ", "channel-1", "TEST");

        assertThatThrownBy(() -> guard.run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PORTONE_STORE_ID");
    }

    @Test
    void enabledPortOneRequiresChannelKey() {
        PaymentConfigurationGuard guard =
                new PaymentConfigurationGuard("local", true, "api-secret", "webhook-secret", "store-1", " ", "TEST");

        assertThatThrownBy(() -> guard.run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PORTONE_CHANNEL_KEY");
    }

    @Test
    void enabledPortOneRejectsUnknownChannelType() {
        PaymentConfigurationGuard guard = new PaymentConfigurationGuard(
                "local", true, "api-secret", "webhook-secret", "store-1", "channel-1", "SANDBOX");

        assertThatThrownBy(() -> guard.run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PORTONE_CHANNEL_TYPE");
    }

    @Test
    void localStubAndConfiguredProductionAreAllowed() {
        assertThatCode(() -> new PaymentConfigurationGuard("local", false, "", "", "", "", "TEST").run(NO_ARGS))
                .doesNotThrowAnyException();
        assertThatCode(() -> new PaymentConfigurationGuard(
                                "prod", true, "api-secret", "webhook-secret", "store-1", "channel-1", "live")
                        .run(NO_ARGS))
                .doesNotThrowAnyException();
    }
}
