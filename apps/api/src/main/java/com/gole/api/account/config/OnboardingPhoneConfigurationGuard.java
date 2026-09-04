package com.gole.api.account.config;

import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 공개 환경에서 필수 전화 인증을 켜 놓고 실제 발송 수단을 빠뜨리는 설정을 기동 단계에서 차단한다. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class OnboardingPhoneConfigurationGuard implements ApplicationRunner {

    private static final Set<String> PUBLIC_ENVIRONMENTS = Set.of("staging", "production", "prod");

    private final String environment;
    private final boolean phoneVerificationRequired;
    private final boolean coolsmsEnabled;
    private final String phoneTemplateId;

    public OnboardingPhoneConfigurationGuard(
            @Value("${gole.environment:local}") String environment,
            @Value("${gole.onboarding.phone-verification-required:true}") boolean phoneVerificationRequired,
            @Value("${coolsms.enabled:false}") boolean coolsmsEnabled,
            @Value("${gole.onboarding.phone-verification-template-id:}") String phoneTemplateId) {
        this.environment = environment == null ? "" : environment.trim().toLowerCase(Locale.ROOT);
        this.phoneVerificationRequired = phoneVerificationRequired;
        this.coolsmsEnabled = coolsmsEnabled;
        this.phoneTemplateId = phoneTemplateId == null ? "" : phoneTemplateId;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!PUBLIC_ENVIRONMENTS.contains(environment) || !phoneVerificationRequired) {
            return;
        }
        if (!coolsmsEnabled) {
            throw new IllegalStateException(
                    "Public environments requiring phone verification must set COOLSMS_ENABLED=true");
        }
        if (phoneTemplateId.isBlank()) {
            throw new IllegalStateException(
                    "Public environments requiring phone verification must set GOLE_ONBOARDING_PHONE_TEMPLATE_ID");
        }
    }
}
