package com.gole.api.account.adapter.out.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.gole.api.account.domain.model.Email;
import com.gole.api.account.domain.model.VerificationCode;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpVerificationCodeSenderAdapterTest {

    @Test
    void sendsVerificationCodeWithConfiguredSender() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SmtpVerificationCodeSenderAdapter adapter =
                new SmtpVerificationCodeSenderAdapter(mailSender, "no-reply@gole.example");

        adapter.send(new Email("member@example.com"), new VerificationCode("123456", Instant.EPOCH));

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getFrom()).isEqualTo("no-reply@gole.example");
        assertThat(message.getValue().getTo()).containsExactly("member@example.com");
        assertThat(message.getValue().getText()).contains("123456", "10분");
    }
}
