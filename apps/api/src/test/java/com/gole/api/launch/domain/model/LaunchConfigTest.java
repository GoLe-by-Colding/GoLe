package com.gole.api.launch.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LaunchConfigTest {

    private static LaunchConfig at(LaunchStage stage) {
        return new LaunchConfig(stage, Map.of(), null, null);
    }

    @ParameterizedTest(name = "{0}단계 → {1}")
    @CsvSource({"0,DIRECT_CHAT", "1,DIRECT_CHAT", "2,MANUAL_SETTLEMENT", "3,PARTNER_PAYOUT"})
    @DisplayName("거래 모델은 단계에서 파생된다")
    void tradeModeIsDerivedFromStage(int level, TradeMode expected) {
        assertThat(at(LaunchStage.ofLevel(level)).tradeMode()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}단계에서 플랫폼이 돈을 만지는가 = {1}")
    @CsvSource({"0,false", "1,false", "2,true", "3,true"})
    @DisplayName("직거래 단계에서는 플랫폼이 돈을 만지지 않는다")
    void platformHandlesMoneyOnlyFromTrading(int level, boolean expected) {
        assertThat(at(LaunchStage.ofLevel(level)).platformHandlesMoney()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}단계 기본 개방: payments={1} reviews={2} partnerPayout={3}")
    @CsvSource({
        "0,false,false,false",
        "1,false,false,false",
        "2,true,true,false",
        "3,true,true,true",
    })
    @DisplayName("기능 기본값은 단계로 결정된다")
    void featureDefaultsFollowStage(int level, boolean payments, boolean reviews, boolean partnerPayout) {
        LaunchConfig config = at(LaunchStage.ofLevel(level));

        assertThat(config.isEnabled(LaunchFeature.PAYMENTS)).isEqualTo(payments);
        assertThat(config.isEnabled(LaunchFeature.REVIEWS)).isEqualTo(reviews);
        assertThat(config.isEnabled(LaunchFeature.PARTNER_PAYOUT)).isEqualTo(partnerPayout);
    }

    @Test
    @DisplayName("override 는 단계 기본값을 양방향으로 뒤집는다")
    void overrideWinsOverStageDefault() {
        LaunchConfig closedPayments =
                new LaunchConfig(LaunchStage.FULL, Map.of(LaunchFeature.PAYMENTS, false), null, null);
        LaunchConfig openedReviews =
                new LaunchConfig(LaunchStage.PREPARING, Map.of(LaunchFeature.REVIEWS, true), null, null);

        assertThat(closedPayments.isEnabled(LaunchFeature.PAYMENTS)).isFalse();
        assertThat(openedReviews.isEnabled(LaunchFeature.REVIEWS)).isTrue();
    }

    @Test
    @DisplayName("override 해제는 단계 기본값으로 되돌린다")
    void clearingOverrideFallsBackToStageDefault() {
        LaunchConfig config = new LaunchConfig(LaunchStage.FULL, Map.of(LaunchFeature.PAYMENTS, false), null, null);

        LaunchConfig cleared = config.withOverride(LaunchFeature.PAYMENTS, null, null, "admin-1");

        assertThat(cleared.overrides()).doesNotContainKey(LaunchFeature.PAYMENTS);
        assertThat(cleared.isEnabled(LaunchFeature.PAYMENTS)).isTrue();
    }

    @Test
    @DisplayName("저장된 설정이 없으면 당근형 직거래로 안전하게 시작한다")
    void unsetStartsWithDirectChatOnly() {
        LaunchConfig unset = LaunchConfig.unset();

        assertThat(unset.stage()).isEqualTo(LaunchStage.BROWSE_ONLY);
        assertThat(unset.platformHandlesMoney()).isFalse();
        assertThat(unset.updatedAt()).isNull();
    }

    @Test
    @DisplayName("결제와 자동지급은 최소 단계보다 낮은 곳에서 override로 우회할 수 없다")
    void moneyFeaturesCannotBypassTheirMinimumStage() {
        LaunchConfig preparing = new LaunchConfig(
                LaunchStage.PREPARING,
                Map.of(LaunchFeature.PAYMENTS, true, LaunchFeature.PARTNER_PAYOUT, true),
                null,
                null);

        assertThat(preparing.isEnabled(LaunchFeature.PAYMENTS)).isFalse();
        assertThat(preparing.isEnabled(LaunchFeature.PARTNER_PAYOUT)).isFalse();
    }

    @Test
    @DisplayName("결제를 긴급 차단하면 자동지급도 함께 닫힌다")
    void partnerPayoutDependsOnPayments() {
        LaunchConfig full = new LaunchConfig(LaunchStage.FULL, Map.of(LaunchFeature.PAYMENTS, false), null, null);

        assertThat(full.isEnabled(LaunchFeature.PAYMENTS)).isFalse();
        assertThat(full.isEnabled(LaunchFeature.PARTNER_PAYOUT)).isFalse();
    }

    @Test
    @DisplayName("범위를 벗어난 단계 값은 거부한다")
    void unknownStageLevelIsRejected() {
        assertThatThrownBy(() -> LaunchStage.ofLevel(4)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("기능 이름은 enum 이름과 API 이름 양쪽으로 찾는다")
    void featureLookupAcceptsBothNames() {
        assertThat(LaunchFeature.of("partnerPayout")).isEqualTo(LaunchFeature.PARTNER_PAYOUT);
        assertThat(LaunchFeature.of("PARTNER_PAYOUT")).isEqualTo(LaunchFeature.PARTNER_PAYOUT);
        assertThatThrownBy(() -> LaunchFeature.of("nope")).isInstanceOf(IllegalArgumentException.class);
    }
}
