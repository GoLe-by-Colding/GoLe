package com.gole.api.common.config;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 로컬 전용 어댑터와 샘플 데이터가 운영에서 실수로 활성화되는 것을 기동 단계에서 차단한다. */
@Component
public class ProductionConfigurationGuard implements ApplicationRunner {

    private final String environment;
    private final boolean verificationEmailEnabled;
    private final Map<String, Boolean> seedFlags;

    public ProductionConfigurationGuard(
            @Value("${gole.environment:local}") String environment,
            @Value("${gole.verification.email.enabled:false}") boolean verificationEmailEnabled,
            @Value("${gole.catalog.seed-on-empty:true}") boolean catalogSeed,
            @Value("${gole.listing.seed-on-empty:true}") boolean listingSeed,
            @Value("${gole.pricing.seed-on-empty:true}") boolean pricingSeed,
            @Value("${gole.community.seed-on-empty:true}") boolean communitySeed,
            @Value("${gole.report.seed-on-empty:true}") boolean reportSeed,
            @Value("${gole.media.seed-on-startup:true}") boolean mediaSeed) {
        this.environment = environment;
        this.verificationEmailEnabled = verificationEmailEnabled;
        this.seedFlags = Map.of(
                "catalog", catalogSeed,
                "listing", listingSeed,
                "pricing", pricingSeed,
                "community", communitySeed,
                "report", reportSeed,
                "media", mediaSeed);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!isProduction()) {
            return;
        }
        if (!verificationEmailEnabled) {
            throw new IllegalStateException(
                    "Production must enable verification email; refusing to log verification codes");
        }
        String enabledSeeds = seedFlags.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .sorted()
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        if (!enabledSeeds.isBlank()) {
            throw new IllegalStateException("Production must disable sample data seeds: " + enabledSeeds);
        }
    }

    private boolean isProduction() {
        return "production".equalsIgnoreCase(environment) || "prod".equalsIgnoreCase(environment);
    }
}
