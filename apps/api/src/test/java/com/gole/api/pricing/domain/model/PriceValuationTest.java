package com.gole.api.pricing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class PriceValuationTest {

    @Test
    void modelAppliesConditionDepreciationAndSpreads() {
        PriceValuation.ConditionValuation sealed = PriceValuation.model(SetCondition.NEW_SEALED, 1_000_000);
        assertThat(sealed.depreciationPct()).isZero();
        assertThat(sealed.fairPrice()).isEqualTo(1_000_000);
        assertThat(sealed.basis()).isEqualTo(ValuationBasis.MODEL);
        assertThat(sealed.basedOnRealData()).isFalse();
        assertThat(sealed.sampleCount()).isZero();
        assertThat(sealed.sellPrice()).isLessThan(sealed.fairPrice());
        assertThat(sealed.buyPrice()).isGreaterThan(sealed.fairPrice());

        PriceValuation.ConditionValuation good = PriceValuation.model(SetCondition.USED_GOOD, 1_000_000);
        assertThat(good.depreciationPct()).isEqualTo(22);
        assertThat(good.fairPrice()).isEqualTo(780_000);

        PriceValuation.ConditionValuation fair = PriceValuation.model(SetCondition.USED_FAIR, 1_000_000);
        assertThat(fair.fairPrice()).isLessThan(good.fairPrice());
    }

    @Test
    void modelPricesDescendMonotonicallyByGrade() {
        // 등급이 낮아질수록 공정가가 반드시 낮아져야 한다. 계수를 손대다 순서가 뒤집히면 여기서 잡힌다.
        long previous = Long.MAX_VALUE;
        for (SetCondition condition : SetCondition.values()) {
            long fair = PriceValuation.model(condition, 1_000_000).fairPrice();
            assertThat(fair).isLessThan(previous);
            previous = fair;
        }
    }

    @Test
    void gradeUsesActualFairAndDerivesDepreciation() {
        PriceValuation.ConditionValuation real = PriceValuation.grade(SetCondition.USED_GOOD, 1_000_000, 700_000, 5);
        assertThat(real.basis()).isEqualTo(ValuationBasis.GRADE);
        assertThat(real.basedOnRealData()).isTrue();
        assertThat(real.sampleCount()).isEqualTo(5);
        assertThat(real.fairPrice()).isEqualTo(700_000);
        assertThat(real.depreciationPct()).isEqualTo(30);
    }

    @Test
    void groupAnchorsOnGroupMedianAndRescalesWithinGroup() {
        // COMPLETE 그룹 = LIKE_NEW(0.88) + USED_GOOD(0.78) → 대표계수 0.83.
        // 그룹 중앙값 830,000이면 LIKE_NEW는 830,000 * 0.88/0.83 ≈ 880,000.
        double reference = ConditionGroup.COMPLETE.referenceFactor();
        assertThat(reference).isCloseTo(0.83, within(1e-9));

        PriceValuation.ConditionValuation likeNew = PriceValuation.group(SetCondition.LIKE_NEW, 1_000_000, 830_000, 4);
        assertThat(likeNew.basis()).isEqualTo(ValuationBasis.GROUP);
        assertThat(likeNew.basedOnRealData()).isTrue();
        assertThat(likeNew.sampleCount()).isEqualTo(4);
        assertThat(likeNew.fairPrice()).isEqualTo(Math.round(830_000 * (0.88 / reference)));

        // 같은 그룹 앵커라도 등급이 낮으면 더 싸야 한다.
        PriceValuation.ConditionValuation good = PriceValuation.group(SetCondition.USED_GOOD, 1_000_000, 830_000, 4);
        assertThat(good.fairPrice()).isLessThan(likeNew.fairPrice());
    }

    @Test
    void groupAnchorBeatsModelWhenGroupTradesHigherThanTheModelAssumes() {
        // 감가 모델은 미개봉 시세에서 통째로 외삽한다. 그룹 실측이 모델 가정보다 비싸게
        // 형성돼 있으면 그룹 앵커가 그 사실을 반영해야 한다.
        long marketPrice = 1_000_000;
        long groupMedian = 900_000; // 모델이 가정한 COMPLETE 대표가(830,000)보다 높게 체결됨

        long modelFair =
                PriceValuation.model(SetCondition.USED_GOOD, marketPrice).fairPrice();
        long groupFair = PriceValuation.group(SetCondition.USED_GOOD, marketPrice, groupMedian, 6)
                .fairPrice();

        assertThat(groupFair).isGreaterThan(modelFair);
    }

    @Test
    void groupNeverProducesNegativeDepreciation() {
        // 그룹 체결가가 미개봉 시세보다 높은 이상 상황에서도 감가율이 음수로 새지 않아야 한다.
        PriceValuation.ConditionValuation c = PriceValuation.group(SetCondition.LIKE_NEW, 500_000, 900_000, 3);
        assertThat(c.depreciationPct()).isZero();
        assertThat(c.fairPrice()).isPositive();
    }
}
