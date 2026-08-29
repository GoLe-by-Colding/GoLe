package com.gole.api.common.config;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 로컬 전용 어댑터와 샘플 데이터가 운영에서 실수로 활성화되는 것을 기동 단계에서 차단한다. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProductionConfigurationGuard implements ApplicationRunner {

    private final String environment;
    private final boolean verificationEmailEnabled;
    private final Map<String, Boolean> seedFlags;
    private final boolean demoPricingEvidence;
    private final boolean legacyPricingEvidence;

    public ProductionConfigurationGuard(
            @Value("${gole.environment:local}") String environment,
            @Value("${gole.verification.email.enabled:false}") boolean verificationEmailEnabled,
            @Value("${gole.catalog.seed-on-empty:false}") boolean catalogSeed,
            @Value("${gole.listing.seed-on-empty:false}") boolean listingSeed,
            @Value("${gole.pricing.seed-on-empty:false}") boolean pricingSeed,
            @Value("${gole.community.seed-on-empty:false}") boolean communitySeed,
            @Value("${gole.report.seed-on-empty:false}") boolean reportSeed,
            @Value("${gole.review.seed-on-empty:false}") boolean reviewSeed,
            @Value("${gole.media.seed-on-startup:false}") boolean mediaSeed,
            @Value("${gole.pricing.evidence.include-demo:false}") boolean demoPricingEvidence,
            @Value("${gole.pricing.evidence.include-legacy:false}") boolean legacyPricingEvidence) {
        this.environment = environment;
        this.verificationEmailEnabled = verificationEmailEnabled;
        this.demoPricingEvidence = demoPricingEvidence;
        this.legacyPricingEvidence = legacyPricingEvidence;
        this.seedFlags = Map.of(
                "catalog", catalogSeed,
                "listing", listingSeed,
                "pricing", pricingSeed,
                "community", communitySeed,
                "report", reportSeed,
                "review", reviewSeed,
                "media", mediaSeed);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (isProduction() && !verificationEmailEnabled) {
            throw new IllegalStateException(
                    "Production must enable verification email; refusing to log verification codes");
        }
        if (!isPublicEnvironment()) {
            return;
        }
        if (demoPricingEvidence || legacyPricingEvidence) {
            throw new IllegalStateException(
                    "Public environments must exclude demo and unverified legacy pricing evidence");
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

    private boolean isPublicEnvironment() {
        return isProduction() || "staging".equalsIgnoreCase(environment);
    }
}
