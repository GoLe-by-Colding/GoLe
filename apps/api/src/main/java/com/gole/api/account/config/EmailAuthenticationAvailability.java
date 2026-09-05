package com.gole.api.account.config;

import com.gole.api.common.exception.ServiceUnavailableException;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 이메일 발송이 없는 공개 환경에서 인증 challenge가 생성되거나 로그로 노출되지 않게 막는다. */
@Component
public class EmailAuthenticationAvailability {

    public static final String UNAVAILABLE_CODE = "EMAIL_AUTHENTICATION_UNAVAILABLE";
    private static final String UNAVAILABLE_MESSAGE = "이메일 인증 발송을 준비 중입니다. 기존 계정 로그인과 소셜 로그인은 계속 이용할 수 있습니다";
    private static final Set<String> DEVELOPER_ENVIRONMENTS = Set.of("local", "development", "dev", "test", "e2e");

    private final boolean available;

    public EmailAuthenticationAvailability(
            @Value("${gole.environment:local}") String environment,
            @Value("${gole.verification.email.enabled:false}") boolean emailDeliveryEnabled) {
        String normalized = environment == null ? "" : environment.trim().toLowerCase(Locale.ROOT);
        this.available = emailDeliveryEnabled || DEVELOPER_ENVIRONMENTS.contains(normalized);
    }

    /** 실제 메일 또는 명시적인 개발용 로그 전달 수단이 있을 때만 true다. */
    public boolean available() {
        return available;
    }

    public void requireAvailable() {
        if (!available) {
            throw new ServiceUnavailableException(UNAVAILABLE_CODE, UNAVAILABLE_MESSAGE);
        }
    }
}
