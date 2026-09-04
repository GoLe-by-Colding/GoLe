package com.gole.api.chat.config;

import com.gole.api.chat.application.SupportNotificationOutboxProperties;
import com.gole.api.common.operations.DiscordOperationsProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 문의 Discord 알림과 durable outbox processor가 서로 다른 상태로 기동되는 오류를 차단한다. */
@Component
public class SupportNotificationOutboxConfigurationGuard implements ApplicationRunner {

    private final DiscordOperationsProperties discord;
    private final SupportNotificationOutboxProperties outbox;

    public SupportNotificationOutboxConfigurationGuard(
            DiscordOperationsProperties discord, SupportNotificationOutboxProperties outbox) {
        this.discord = discord;
        this.outbox = outbox;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (discord.isEnabled() != outbox.isProcessingEnabled()) {
            throw new IllegalStateException(
                    "Discord alerts and the support notification outbox processor must be enabled together");
        }
    }
}
