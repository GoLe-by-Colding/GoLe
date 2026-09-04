package com.gole.api.common.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 공개환경 CORS를 GoLe HTTPS 오리진 둘로 고정해 Secret 오염으로 범위가 넓어지는 것을 막는다. */
@Component
public class WebCorsConfigurationGuard implements ApplicationRunner {

    private static final Set<String> DEVELOPER_ENVIRONMENTS = Set.of("local", "development", "dev", "test", "e2e");
    private static final Set<String> PUBLIC_ORIGINS = Set.of("https://gole.co.kr", "https://www.gole.co.kr");

    private final String environment;
    private final String[] allowedOrigins;

    public WebCorsConfigurationGuard(
            @Value("${gole.environment:local}") String environment,
            @Value("${gole.web.allowed-origins:http://localhost:3000,http://localhost:3010}") String[] allowedOrigins) {
        this.environment = environment == null ? "" : environment.trim().toLowerCase(Locale.ROOT);
        this.allowedOrigins = allowedOrigins == null ? new String[0] : allowedOrigins.clone();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!requiresPublicSafety()) {
            return;
        }
        Set<String> configured = Arrays.stream(allowedOrigins)
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .collect(Collectors.toUnmodifiableSet());
        if (!PUBLIC_ORIGINS.equals(configured) || configured.size() != allowedOrigins.length) {
            throw new IllegalStateException(
                    "Public CORS origins must be exactly https://gole.co.kr and https://www.gole.co.kr");
        }
    }

    private boolean requiresPublicSafety() {
        return !DEVELOPER_ENVIRONMENTS.contains(environment);
    }
}
