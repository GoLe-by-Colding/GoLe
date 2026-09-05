package com.gole.api.common.operations;

import java.net.URI;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Discord 관제를 켠 채 역할별 목적지를 빠뜨리는 운영 설정 오류를 부팅 시점에 차단한다. */
@Component
public class DiscordConfigurationGuard implements ApplicationRunner {

    private static final Set<String> ALLOWED_HOSTS = Set.of("discord.com", "discordapp.com");
    private static final Pattern WEBHOOK_PATH = Pattern.compile("^/api/webhooks/[0-9]+/[A-Za-z0-9._-]+/?$");

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
        // 가입·결제·문의·운영 목적지를 각각 강제해 잘못된 채널 라우팅을 부팅 시점에 발견한다.
        requireDiscordWebhook("ACCOUNT", properties.getAccountWebhookUrl());
        requireDiscordWebhook("PAYMENT", properties.getPaymentWebhookUrl());
        requireDiscordWebhook("SUPPORT", properties.getSupportWebhookUrl());
        requireDiscordWebhook("OPERATIONS", properties.getOperationsWebhookUrl());
    }

    private static void requireDiscordWebhook(String role, String destination) {
        if (destination == null || destination.isBlank()) {
            throw new IllegalStateException(
                    "Discord alerts are enabled but no role-specific webhook is configured for " + role);
        }
        try {
            URI uri = URI.create(destination);
            boolean valid = "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && ALLOWED_HOSTS.contains(uri.getHost())
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && uri.getRawUserInfo() == null
                    && uri.getRawFragment() == null
                    && WEBHOOK_PATH.matcher(uri.getRawPath()).matches();
            if (!valid) {
                throw invalidDestination(role);
            }
        } catch (IllegalArgumentException exception) {
            throw invalidDestination(role);
        }
    }

    private static IllegalStateException invalidDestination(String role) {
        // webhook 토큰을 예외 메시지에 포함하지 않는다. 이 메시지는 운영 로그에 남을 수 있다.
        return new IllegalStateException("Discord webhook configuration is invalid for " + role);
    }
}
