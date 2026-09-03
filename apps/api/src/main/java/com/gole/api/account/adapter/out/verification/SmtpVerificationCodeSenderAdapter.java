package com.gole.api.account.adapter.out.verification;

import com.gole.api.account.application.port.out.VerificationCodeSenderPort;
import com.gole.api.account.domain.model.Email;
import com.gole.api.account.domain.model.VerificationCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** 운영 SMTP 인증 메일 어댑터. 비밀번호와 호스트는 Spring mail 환경변수로만 주입한다. */
@Component
@ConditionalOnProperty(name = "gole.verification.email.enabled", havingValue = "true")
public class SmtpVerificationCodeSenderAdapter implements VerificationCodeSenderPort {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpVerificationCodeSenderAdapter(
            JavaMailSender mailSender, @Value("${gole.verification.email.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(Email email, VerificationCode code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email.value());
        message.setSubject("[GoLe] 이메일 인증 코드");
        message.setText("GoLe 이메일 인증 코드는 " + code.code() + " 입니다.\n\n10분 안에 입력해 주세요.");
        mailSender.send(message);
    }

    @Override
    public void sendPasswordReset(Email email, VerificationCode code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email.value());
        message.setSubject("[GoLe] 비밀번호 재설정 코드");
        message.setText("GoLe 비밀번호 재설정 코드는 " + code.code() + " 입니다.\n\n10분 안에 입력해 주세요. 요청하지 않았다면 이 메일을 무시해 주세요.");
        mailSender.send(message);
    }
}
