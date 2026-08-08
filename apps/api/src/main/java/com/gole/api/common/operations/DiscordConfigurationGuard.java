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
        for (OperationalEvent.Category category : OperationalEvent.Category.values()) {
            String destination = properties.webhookFor(category);
            if (destination == null || destination.isBlank()) {
                throw new IllegalStateException(
                        "Discord alerts are enabled but no webhook is configured for " + category.name());
            }
        }
    }
}
