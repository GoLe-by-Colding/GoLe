package com.gole.api.order.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 플랫폼 수수료 정책 불변식·계산. (shipping-and-fees R5) */
class FeePolicyTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void feeFor_appliesRateAndRoundsToWon() {
        FeePolicy policy = new FeePolicy(0.05, 0, 0);
        // 280,000 * 0.05 = 14,000
        assertThat(policy.feeFor(280_000)).isEqualTo(14_000);
        // 반올림: 33,333 * 0.05 = 1666.65 → 1667
        assertThat(policy.feeFor(33_333)).isEqualTo(1_667);
    }

    @Test
    void feeFor_appliesMinimum() {
        FeePolicy policy = new FeePolicy(0.05, 1_000, 0);
        // 10,000 * 0.05 = 500 → 하한 1,000
        assertThat(policy.feeFor(10_000)).isEqualTo(1_000);
    }

    @Test
    void feeFor_appliesMaximum() {
        FeePolicy policy = new FeePolicy(0.05, 0, 50_000);
        // 5,000,000 * 0.05 = 250,000 → 상한 50,000
        assertThat(policy.feeFor(5_000_000)).isEqualTo(50_000);
    }

    @Test
    void feeFor_zeroMaxMeansNoCap() {
        FeePolicy policy = new FeePolicy(0.05, 0, 0);
        assertThat(policy.feeFor(5_000_000)).isEqualTo(250_000);
    }

    /** 하한이 거래액보다 큰 소액 거래에서도 정산액이 음수가 되면 안 된다. */
    @Test
    void feeFor_neverExceedsGross() {
        FeePolicy policy = new FeePolicy(0.05, 10_000, 0);
        assertThat(policy.feeFor(3_000)).isEqualTo(3_000);
    }

    @Test
    void feeFor_zeroOrNegativeGrossYieldsNoFee() {
        FeePolicy policy = new FeePolicy(0.05, 1_000, 0);
        assertThat(policy.feeFor(0)).isZero();
        assertThat(policy.feeFor(-1)).isZero();
    }

    @Test
    void constructor_rejectsRateOutOfRange() {
        assertThatThrownBy(() -> new FeePolicy(-0.01, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FeePolicy(1.01, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_rejectsMinAboveMax() {
        assertThatThrownBy(() -> new FeePolicy(0.05, 10_000, 5_000)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void settlement_preservesAppliedRateAndBalances() {
        FeePolicy policy = new FeePolicy(0.03, 0, 0);
        Settlement s = Settlement.compute("o1", "seller-1", 100_000, policy, NOW);

        assertThat(s.fee()).isEqualTo(3_000);
        assertThat(s.payout()).isEqualTo(97_000);
        // 적용 요율이 보존돼야 과거 정산을 재현할 수 있다. (R5.2)
        assertThat(s.feeRate()).isEqualTo(0.03);
        // payout = gross - fee 가 항상 성립. (R5.4)
        assertThat(s.grossAmount()).isEqualTo(s.fee() + s.payout());
    }
}
