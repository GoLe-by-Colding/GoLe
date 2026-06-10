package com.gole.api.pricing.domain.model;

import java.util.List;

/**
 * 상태별 시세 밸류에이션. 시장 체결가(미개봉 기준)에서 상태별 공정 시세를 산출하고,
 * 각 상태마다 즉시판매(매도)·즉시구매(매수) 호가를 스프레드로 산출한다. (KREAM식 호가)
 *
 * <p>상태별 공정가는 실제 상태별 체결 데이터가 충분하면 그 값으로, 아니면 감가 모델로 산출한다.
 * {@code basedOnRealData}/{@code sampleCount}로 근거를 노출한다.
 */
public record PriceValuation(String setNumber, long marketPrice, List<ConditionValuation> conditions) {

    /** 즉시판매(매도) 스프레드 — 공정가보다 약간 낮게. */
    public static final double SELL_SPREAD = 0.96;
    /** 즉시구매(매수) 스프레드 — 공정가보다 약간 높게. */
    public static final double BUY_SPREAD = 1.05;

    public record ConditionValuation(
            SetCondition condition,
            int depreciationPct,
            long fairPrice,
            long sellPrice,
            long buyPrice,
            int sampleCount,
            boolean basedOnRealData) {}

    /** 감가 모델 기반(실데이터 부족 시 폴백). */
    public static ConditionValuation model(SetCondition condition, long marketPrice) {
        long fair = Math.round(marketPrice * condition.factor());
        return spread(condition, fair, condition.depreciationPct(), 0, false);
    }

    /** 실제 상태별 체결가 기반. */
    public static ConditionValuation real(
            SetCondition condition, long marketPrice, long fairPrice, int sampleCount) {
        int dep = marketPrice <= 0
                ? 0
                : Math.max(0, (int) Math.round((1.0 - (double) fairPrice / marketPrice) * 100));
        return spread(condition, fairPrice, dep, sampleCount, true);
    }

    private static ConditionValuation spread(
            SetCondition condition, long fair, int dep, int sampleCount, boolean real) {
        long sell = Math.round(fair * SELL_SPREAD);
        long buy = Math.round(fair * BUY_SPREAD);
        return new ConditionValuation(condition, dep, fair, sell, buy, sampleCount, real);
    }
}
