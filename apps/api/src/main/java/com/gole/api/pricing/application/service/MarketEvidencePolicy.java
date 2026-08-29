package com.gole.api.pricing.application.service;

import com.gole.api.pricing.domain.model.PriceTransaction;
import com.gole.api.pricing.domain.model.PriceTransactionSource;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 시세·트렌딩에 포함할 증빙 출처를 한곳에서 결정한다. */
@Component
public class MarketEvidencePolicy {

    private final Set<PriceTransactionSource> includedSources;

    public MarketEvidencePolicy(
            @Value("${gole.pricing.evidence.include-demo:false}") boolean includeDemo,
            @Value("${gole.pricing.evidence.include-legacy:false}") boolean includeLegacy) {
        EnumSet<PriceTransactionSource> sources = EnumSet.of(PriceTransactionSource.PLATFORM_PAYMENT);
        if (includeDemo) {
            sources.add(PriceTransactionSource.DEMO_SEED);
            sources.add(PriceTransactionSource.PLATFORM_TEST);
        }
        if (includeLegacy) {
            sources.add(PriceTransactionSource.LEGACY_UNVERIFIED);
        }
        this.includedSources = Collections.unmodifiableSet(sources);
    }

    public boolean includes(PriceTransaction transaction) {
        return includedSources.contains(transaction.source());
    }

    public Set<PriceTransactionSource> includedSources() {
        return includedSources;
    }

    public boolean includesDemoEvidence() {
        return includedSources.contains(PriceTransactionSource.DEMO_SEED)
                || includedSources.contains(PriceTransactionSource.PLATFORM_TEST);
    }
}
