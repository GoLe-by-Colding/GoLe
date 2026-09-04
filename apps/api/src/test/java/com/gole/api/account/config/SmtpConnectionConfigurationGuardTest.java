package com.gole.api.account.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.mail.AuthenticationFailedException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class SmtpConnectionConfigurationGuardTest {

    private static final DefaultApplicationArguments NO_ARGS = new DefaultApplicationArguments();

    @Test
    void validSmtpConnectionAllowsStartup() {
        var guard = new SmtpConnectionConfigurationGuard(() -> {});

        assertThatCode(() -> guard.run(NO_ARGS)).doesNotThrowAnyException();
    }

    @Test
    void authenticationFailureBlocksStartupWithoutEchoingProviderDetails() {
        var guard = new SmtpConnectionConfigurationGuard(() -> {
            throw new AuthenticationFailedException("user@example.com secret-detail");
        });

        assertThatThrownBy(() -> guard.run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Verification email SMTP connection failed")
                .hasMessageNotContaining("secret-detail");
    }
}
