package com.gole.api.pricing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PriceValuationTest {

    @Test
    void appliesConditionDepreciationAndSpreads() {
        PriceValuation valuation = PriceValuation.fromMarketPrice("10307", 1_000_000);

        assertThat(valuation.conditions()).hasSize(3);

        PriceValuation.ConditionValuation sealed = valuation.conditions().get(0);
        assertThat(sealed.condition()).isEqualTo(SetCondition.NEW_SEALED);
        assertThat(sealed.depreciationPct()).isZero();
        assertThat(sealed.fairPrice()).isEqualTo(1_000_000);
        // 즉시판매(매도)는 공정가보다 낮고, 즉시구매(매수)는 높다.
        assertThat(sealed.sellPrice()).isLessThan(sealed.fairPrice());
        assertThat(sealed.buyPrice()).isGreaterThan(sealed.fairPrice());

        PriceValuation.ConditionValuation used = valuation.conditions().get(1);
        assertThat(used.condition()).isEqualTo(SetCondition.USED_COMPLETE);
        assertThat(used.depreciationPct()).isEqualTo(22);
        assertThat(used.fairPrice()).isEqualTo(780_000);

        PriceValuation.ConditionValuation incomplete = valuation.conditions().get(2);
        assertThat(incomplete.condition()).isEqualTo(SetCondition.USED_INCOMPLETE);
        assertThat(incomplete.fairPrice()).isLessThan(used.fairPrice());
    }
}
