package com.gole.api.common.operations;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class DiscordConfigurationGuardTest {

    private static final DefaultApplicationArguments NO_ARGS = new DefaultApplicationArguments(new String[0]);
    private static final String ACCOUNT_WEBHOOK = "https://discord.com/api/webhooks/1/account-token";
    private static final String PAYMENT_WEBHOOK = "https://discord.com/api/webhooks/2/payment-token";
    private static final String SUPPORT_WEBHOOK = "https://discord.com/api/webhooks/3/support-token";
    private static final String OPERATIONS_WEBHOOK = "https://discord.com/api/webhooks/4/operations-token";

    @Test
    void enabledAlertsRequireDestinationForEveryCategory() {
        DiscordOperationsProperties properties = new DiscordOperationsProperties();
        properties.setEnabled(true);
        properties.setAccountWebhookUrl(ACCOUNT_WEBHOOK);
        properties.setPaymentWebhookUrl(PAYMENT_WEBHOOK);
        properties.setSupportWebhookUrl(SUPPORT_WEBHOOK);

        assertThatThrownBy(() -> new DiscordConfigurationGuard(properties).run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OPERATIONS");
    }

    @Test
    void genericWebhookDoesNotReplaceRoleSpecificDestinations() {
        DiscordOperationsProperties properties = new DiscordOperationsProperties();
        properties.setEnabled(true);
        properties.setWebhookUrl("https://discord.com/api/webhooks/5/generic-token");

        assertThatThrownBy(() -> new DiscordConfigurationGuard(properties).run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ACCOUNT");
    }

    @Test
    void roleSpecificWebhooksCoverAccountPaymentSupportAndOperations() {
        DiscordOperationsProperties properties = new DiscordOperationsProperties();
        properties.setEnabled(true);
        properties.setAccountWebhookUrl(ACCOUNT_WEBHOOK);
        properties.setPaymentWebhookUrl(PAYMENT_WEBHOOK);
        properties.setSupportWebhookUrl(SUPPORT_WEBHOOK);
        properties.setOperationsWebhookUrl(OPERATIONS_WEBHOOK);

        assertThatCode(() -> new DiscordConfigurationGuard(properties).run(NO_ARGS))
                .doesNotThrowAnyException();
    }

    @Test
    void enabledAlertsRejectMissingSupportDestination() {
        DiscordOperationsProperties properties = new DiscordOperationsProperties();
        properties.setEnabled(true);
        properties.setAccountWebhookUrl(ACCOUNT_WEBHOOK);
        properties.setPaymentWebhookUrl(PAYMENT_WEBHOOK);
        properties.setOperationsWebhookUrl(OPERATIONS_WEBHOOK);

        assertThatThrownBy(() -> new DiscordConfigurationGuard(properties).run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUPPORT");
    }

    @Test
    void enabledAlertsRejectNonDiscordOrInsecureWebhookWithoutEchoingSecret() {
        DiscordOperationsProperties properties = validProperties();
        String secretToken = "must-not-appear-in-logs";
        properties.setSupportWebhookUrl("http://attacker.example/api/webhooks/3/" + secretToken);

        assertThatThrownBy(() -> new DiscordConfigurationGuard(properties).run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUPPORT")
                .hasMessageNotContaining(secretToken)
                .hasMessageNotContaining("attacker.example");
    }

    @Test
    void enabledAlertsRejectDiscordLookalikeHost() {
        DiscordOperationsProperties properties = validProperties();
        properties.setSupportWebhookUrl("https://discord.com.attacker.example/api/webhooks/3/token");

        assertThatThrownBy(() -> new DiscordConfigurationGuard(properties).run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUPPORT");
    }

    private static DiscordOperationsProperties validProperties() {
        DiscordOperationsProperties properties = new DiscordOperationsProperties();
        properties.setEnabled(true);
        properties.setAccountWebhookUrl(ACCOUNT_WEBHOOK);
        properties.setPaymentWebhookUrl(PAYMENT_WEBHOOK);
        properties.setSupportWebhookUrl(SUPPORT_WEBHOOK);
        properties.setOperationsWebhookUrl(OPERATIONS_WEBHOOK);
        return properties;
    }
}
