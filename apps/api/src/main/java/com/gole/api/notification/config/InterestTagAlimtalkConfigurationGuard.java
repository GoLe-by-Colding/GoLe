package com.gole.api.notification.config;

import com.gole.api.notification.application.service.InterestTagAlimtalkProperties;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 활성화한 관심태그 알림톡이 발송 불가능한 구성으로 기동되는 것을 차단한다. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class InterestTagAlimtalkConfigurationGuard implements ApplicationRunner {

    private static final Set<String> PUBLIC_ENVIRONMENTS = Set.of("staging", "production", "prod");

    private final InterestTagAlimtalkProperties properties;
    private final String environment;
    private final boolean coolsmsEnabled;

    public InterestTagAlimtalkConfigurationGuard(
            InterestTagAlimtalkProperties properties,
            @Value("${gole.environment:local}") String environment,
            @Value("${coolsms.enabled:false}") boolean coolsmsEnabled) {
        this.properties = properties;
        this.environment = environment == null ? "" : environment.trim().toLowerCase(Locale.ROOT);
        this.coolsmsEnabled = coolsmsEnabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }
        if (properties.templateId().isBlank()) {
            throw new IllegalStateException(
                    "GOLE_INTEREST_TAG_ALIMTALK_ENABLED=true requires GOLE_INTEREST_TAG_ALIMTALK_TEMPLATE_ID");
        }
        if (PUBLIC_ENVIRONMENTS.contains(environment) && !coolsmsEnabled) {
            throw new IllegalStateException(
                    "Public environments enabling interest-tag alimtalk must set COOLSMS_ENABLED=true");
        }
        if (properties.dailyLimitPerAccount() < 1) {
            throw new IllegalStateException("GOLE_INTEREST_TAG_ALIMTALK_DAILY_LIMIT must be at least 1");
        }
    }
}
