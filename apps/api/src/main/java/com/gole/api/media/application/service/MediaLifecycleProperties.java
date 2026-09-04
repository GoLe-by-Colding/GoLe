package com.gole.api.media.application.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 미디어 수명주기와 삭제 재시도 설정. */
@ConfigurationProperties(prefix = "gole.media.lifecycle")
public record MediaLifecycleProperties(
        Duration stagedTtl,
        Duration maintenanceInterval,
        int batchSize,
        int deletionMaximumAttempts,
        Duration deletionInitialBackoff,
        String replayCompletedSince) {

    public MediaLifecycleProperties {
        stagedTtl = positive(stagedTtl, Duration.ofHours(24));
        maintenanceInterval = positive(maintenanceInterval, Duration.ofMinutes(1));
        batchSize = batchSize <= 0 ? 100 : Math.min(batchSize, 1_000);
        deletionMaximumAttempts = deletionMaximumAttempts <= 0 ? 8 : deletionMaximumAttempts;
        deletionInitialBackoff = positive(deletionInitialBackoff, Duration.ofSeconds(30));
        replayCompletedSince = replayCompletedSince == null ? "" : replayCompletedSince.strip();
    }

    private static Duration positive(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }
}
