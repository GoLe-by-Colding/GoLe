package com.gole.api.chat.config;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 문의 AI를 켠 환경에서 잘못된 gRPC 대상이나 무제한 대기를 기동 단계에서 차단한다. */
@Component
public class SupportAssistantConfigurationGuard implements ApplicationRunner {

    private static final Set<String> DEVELOPER_ENVIRONMENTS = Set.of("local", "development", "dev", "test", "e2e");

    private final String environment;
    private final boolean enabled;
    private final String target;
    private final Duration timeout;

    public SupportAssistantConfigurationGuard(
            @Value("${gole.environment:local}") String environment,
            @Value("${gole.support-agent.enabled:false}") boolean enabled,
            @Value("${gole.support-agent.target:}") String target,
            @Value("${gole.support-agent.timeout:PT2S}") Duration timeout) {
        this.environment = environment == null ? "" : environment.trim().toLowerCase(Locale.ROOT);
        this.enabled = enabled;
        this.target = target;
        this.timeout = timeout;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        if (target == null || target.isBlank()) {
            throw new IllegalStateException("Support agent requires GOLE_SUPPORT_AGENT_GRPC_TARGET");
        }
        if (timeout == null
                || timeout.isZero()
                || timeout.isNegative()
                || timeout.compareTo(Duration.ofSeconds(10)) > 0) {
            throw new IllegalStateException("Support agent timeout must be between 1ms and 10s");
        }
        if (requiresPublicSafety()
                && (!"support-agent:50051".equals(target)
                        || !Duration.ofSeconds(2).equals(timeout))) {
            throw new IllegalStateException(
                    "Public environments must use the internal support-agent:50051 target with a PT2S timeout");
        }
    }

    private boolean requiresPublicSafety() {
        return !DEVELOPER_ENVIRONMENTS.contains(environment);
    }
}
