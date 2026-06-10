package com.gole.api.pricing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PriceValuationTest {

    @Test
    void modelAppliesConditionDepreciationAndSpreads() {
        PriceValuation.ConditionValuation sealed = PriceValuation.model(SetCondition.NEW_SEALED, 1_000_000);
        assertThat(sealed.depreciationPct()).isZero();
        assertThat(sealed.fairPrice()).isEqualTo(1_000_000);
        assertThat(sealed.basedOnRealData()).isFalse();
        assertThat(sealed.sellPrice()).isLessThan(sealed.fairPrice());
        assertThat(sealed.buyPrice()).isGreaterThan(sealed.fairPrice());

        PriceValuation.ConditionValuation used = PriceValuation.model(SetCondition.USED_COMPLETE, 1_000_000);
        assertThat(used.depreciationPct()).isEqualTo(22);
        assertThat(used.fairPrice()).isEqualTo(780_000);

        PriceValuation.ConditionValuation incomplete = PriceValuation.model(SetCondition.USED_INCOMPLETE, 1_000_000);
        assertThat(incomplete.fairPrice()).isLessThan(used.fairPrice());
    }

    @Test
    void realUsesActualFairAndDerivesDepreciation() {
        PriceValuation.ConditionValuation real = PriceValuation.real(SetCondition.USED_COMPLETE, 1_000_000, 700_000, 5);
        assertThat(real.basedOnRealData()).isTrue();
        assertThat(real.sampleCount()).isEqualTo(5);
        assertThat(real.fairPrice()).isEqualTo(700_000);
        assertThat(real.depreciationPct()).isEqualTo(30);
    }
}
