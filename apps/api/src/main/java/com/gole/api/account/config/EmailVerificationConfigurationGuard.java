package com.gole.api.account.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 실제 이메일 인증을 켠 채 불완전한 SMTP 설정으로 가입자를 받는 오류를 기동 단계에서 차단한다. */
@Component
public class EmailVerificationConfigurationGuard implements ApplicationRunner {

    private final boolean enabled;
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String from;
    private final boolean smtpAuth;
    private final boolean startTls;
    private final boolean startTlsRequired;
    private final boolean checkServerIdentity;

    public EmailVerificationConfigurationGuard(
            @Value("${gole.verification.email.enabled:false}") boolean enabled,
            // spring.mail.*의 개발 기본값이 아니라 운영자가 명시한 원본 env를 검사한다.
            // 그렇지 않으면 SMTP_HOST 누락이 localhost:587로 치환돼 가드를 통과한다.
            @Value("${SMTP_HOST:}") String host,
            @Value("${SMTP_PORT:0}") int port,
            @Value("${SMTP_USERNAME:}") String username,
            @Value("${SMTP_PASSWORD:}") String password,
            @Value("${GOLE_VERIFICATION_EMAIL_FROM:}") String from,
            @Value("${SMTP_AUTH:true}") boolean smtpAuth,
            @Value("${SMTP_STARTTLS:true}") boolean startTls,
            @Value("${SMTP_STARTTLS_REQUIRED:true}") boolean startTlsRequired,
            @Value("${SMTP_SSL_CHECKSERVERIDENTITY:true}") boolean checkServerIdentity) {
        this.enabled = enabled;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.from = from;
        this.smtpAuth = smtpAuth;
        this.startTls = startTls;
        this.startTlsRequired = startTlsRequired;
        this.checkServerIdentity = checkServerIdentity;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        requireText("SMTP_HOST", host);
        if (port < 1 || port > 65_535) {
            throw new IllegalStateException("Email verification requires a valid SMTP_PORT");
        }
        requireText("SMTP_USERNAME", username);
        requireText("SMTP_PASSWORD", password);
        requireText("GOLE_VERIFICATION_EMAIL_FROM", from);
        if (!from.contains("@")) {
            throw new IllegalStateException("Email verification requires a valid GOLE_VERIFICATION_EMAIL_FROM");
        }
        if (!smtpAuth || !startTls || !startTlsRequired || !checkServerIdentity) {
            throw new IllegalStateException(
                    "Email verification requires SMTP authentication, mandatory STARTTLS, and hostname verification");
        }
    }

    private static void requireText(String environmentVariable, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Email verification requires " + environmentVariable);
        }
    }
}
