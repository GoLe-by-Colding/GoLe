package com.gole.api.account.adapter.out.verification;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.account.config.EmailAuthenticationAvailability;
import com.gole.api.account.domain.model.Email;
import com.gole.api.account.domain.model.VerificationCode;
import com.gole.api.common.exception.ServiceUnavailableException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LoggingVerificationCodeSenderAdapterTest {

    @Test
    void publicEnvironmentNeverUsesLogsAsAnEmailDeliveryChannel() {
        LoggingVerificationCodeSenderAdapter sender =
                new LoggingVerificationCodeSenderAdapter(new EmailAuthenticationAvailability("production", false));

        assertThatThrownBy(
                        () -> sender.send(new Email("member@gole.test"), new VerificationCode("123456", Instant.EPOCH)))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasFieldOrPropertyWithValue("code", EmailAuthenticationAvailability.UNAVAILABLE_CODE);
    }
}
