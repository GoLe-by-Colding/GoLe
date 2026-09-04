package com.gole.api.chat.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.chat.application.SupportNotificationOutboxProperties;
import com.gole.api.common.operations.DiscordOperationsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class SupportNotificationOutboxConfigurationGuardTest {

    private static final DefaultApplicationArguments NO_ARGS = new DefaultApplicationArguments();

    @Test
    void bothDisabledOrBothEnabledAreConsistent() {
        assertThatCode(() -> guard(false, false).run(NO_ARGS)).doesNotThrowAnyException();
        assertThatCode(() -> guard(true, true).run(NO_ARGS)).doesNotThrowAnyException();
    }

    @Test
    void discordWithoutWorkerOrWorkerWithoutDiscordFailsClosed() {
        assertThatThrownBy(() -> guard(true, false).run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("enabled together");
        assertThatThrownBy(() -> guard(false, true).run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("enabled together");
    }

    private static SupportNotificationOutboxConfigurationGuard guard(boolean discordEnabled, boolean workerEnabled) {
        DiscordOperationsProperties discord = new DiscordOperationsProperties();
        discord.setEnabled(discordEnabled);
        SupportNotificationOutboxProperties outbox = new SupportNotificationOutboxProperties();
        outbox.setProcessingEnabled(workerEnabled);
        return new SupportNotificationOutboxConfigurationGuard(discord, outbox);
    }
}
