package com.gole.api.common.operations;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Discord 관제를 켠 채 역할별 목적지를 빠뜨리는 운영 설정 오류를 부팅 시점에 차단한다. */
@Component
public class DiscordConfigurationGuard implements ApplicationRunner {

    private final DiscordOperationsProperties properties;

    public DiscordConfigurationGuard(DiscordOperationsProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }
        // 운영 관제를 켰다면 일반 fallback 하나로 모든 사건을 한 방에 몰아넣지 않는다.
        // 가입·결제·운영 목적지를 각각 강제해 잘못된 채널 라우팅을 부팅 시점에 발견한다.
        requireDestination("ACCOUNT", properties.getAccountWebhookUrl());
        requireDestination("PAYMENT", properties.getPaymentWebhookUrl());
        requireDestination("OPERATIONS", properties.getOperationsWebhookUrl());
    }

    private static void requireDestination(String role, String destination) {
        if (destination == null || destination.isBlank()) {
            throw new IllegalStateException(
                    "Discord alerts are enabled but no role-specific webhook is configured for " + role);
        }
    }
}
