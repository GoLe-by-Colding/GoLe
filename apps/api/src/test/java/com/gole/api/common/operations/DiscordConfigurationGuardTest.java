package com.gole.api.common.operations;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class DiscordConfigurationGuardTest {

    private static final DefaultApplicationArguments NO_ARGS = new DefaultApplicationArguments(new String[0]);

    @Test
    void enabledAlertsRequireDestinationForEveryCategory() {
        DiscordOperationsProperties properties = new DiscordOperationsProperties();
        properties.setEnabled(true);
        properties.setAccountWebhookUrl("https://discord.example/account");
        properties.setPaymentWebhookUrl("https://discord.example/payment");

        assertThatThrownBy(() -> new DiscordConfigurationGuard(properties).run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OPERATIONS");
    }

    @Test
    void genericWebhookDoesNotReplaceRoleSpecificDestinations() {
        DiscordOperationsProperties properties = new DiscordOperationsProperties();
        properties.setEnabled(true);
        properties.setWebhookUrl("https://discord.example/all");

        assertThatThrownBy(() -> new DiscordConfigurationGuard(properties).run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ACCOUNT");
    }

    @Test
    void roleSpecificWebhooksCoverAccountPaymentAndOperations() {
        DiscordOperationsProperties properties = new DiscordOperationsProperties();
        properties.setEnabled(true);
        properties.setAccountWebhookUrl("https://discord.example/account");
        properties.setPaymentWebhookUrl("https://discord.example/payment");
        properties.setOperationsWebhookUrl("https://discord.example/operations");

        assertThatCode(() -> new DiscordConfigurationGuard(properties).run(NO_ARGS))
                .doesNotThrowAnyException();
    }
}
