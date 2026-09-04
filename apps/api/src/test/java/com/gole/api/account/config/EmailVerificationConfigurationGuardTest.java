package com.gole.api.account.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class EmailVerificationConfigurationGuardTest {

    private static final DefaultApplicationArguments NO_ARGS = new DefaultApplicationArguments();

    @Test
    void disabledEmailDoesNotRequireSmtpCredentials() {
        assertThatCode(() -> guard(false, "", 0, "", "", "").run(NO_ARGS)).doesNotThrowAnyException();
    }

    @Test
    void enabledEmailRequiresEverySmtpCredential() {
        assertThatThrownBy(() -> guard(true, "smtp.gmail.com", 587, "", "app-password", "sender@example.com")
                        .run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SMTP_USERNAME");

        assertThatThrownBy(() -> guard(true, "smtp.gmail.com", 587, "sender@example.com", " ", "sender@example.com")
                        .run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SMTP_PASSWORD");
    }

    @Test
    void enabledEmailRejectsInvalidPortAndSender() {
        assertThatThrownBy(() -> guard(
                                true, "smtp.gmail.com", 0, "sender@example.com", "app-password", "sender@example.com")
                        .run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SMTP_PORT");

        assertThatThrownBy(() -> guard(true, "smtp.gmail.com", 587, "sender@example.com", "app-password", "invalid")
                        .run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GOLE_VERIFICATION_EMAIL_FROM");
    }

    @Test
    void completeGmailSmtpConfigurationIsAllowed() {
        assertThatCode(() -> guard(
                                true,
                                "smtp.gmail.com",
                                587,
                                "coldingcontact@gmail.com",
                                "app-password",
                                "coldingcontact@gmail.com")
                        .run(NO_ARGS))
                .doesNotThrowAnyException();
    }

    @Test
    void enabledEmailRequiresAuthenticationMandatoryTlsAndHostnameVerification() {
        for (int disabledFlag = 0; disabledFlag < 4; disabledFlag++) {
            boolean[] flags = {true, true, true, true};
            flags[disabledFlag] = false;

            assertThatThrownBy(() -> new EmailVerificationConfigurationGuard(
                                    true,
                                    "smtp.gmail.com",
                                    587,
                                    "coldingcontact@gmail.com",
                                    "app-password",
                                    "coldingcontact@gmail.com",
                                    flags[0],
                                    flags[1],
                                    flags[2],
                                    flags[3])
                            .run(NO_ARGS))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("mandatory STARTTLS");
        }
    }

    private static EmailVerificationConfigurationGuard guard(
            boolean enabled, String host, int port, String username, String password, String from) {
        return new EmailVerificationConfigurationGuard(
                enabled, host, port, username, password, from, true, true, true, true);
    }
}
