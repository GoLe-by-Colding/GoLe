package com.gole.api.pricing.domain.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 상태별 시세 밸류에이션. 시장 체결가(새상품 기준)에서 상태별 감가를 적용하고,
 * 각 상태마다 즉시판매(매도)·즉시구매(매수) 호가를 스프레드로 산출한다. (KREAM식 호가)
 *
 * <ul>
 *   <li>fairPrice  = marketPrice × 상태 계수 (감가 적용 공정 시세)
 *   <li>sellPrice  = fairPrice × {@value #SELL_SPREAD} (즉시판매가 — 판매자가 받는 값)
 *   <li>buyPrice   = fairPrice × {@value #BUY_SPREAD}  (즉시구매가 — 구매자가 내는 값)
 * </ul>
 */
public record PriceValuation(String setNumber, long marketPrice, List<ConditionValuation> conditions) {

    /** 즉시판매(매도) 스프레드 — 공정가보다 약간 낮게. */
    private static final double SELL_SPREAD = 0.96;
    /** 즉시구매(매수) 스프레드 — 공정가보다 약간 높게. */
    private static final double BUY_SPREAD = 1.05;

    public record ConditionValuation(
            SetCondition condition,
            int depreciationPct,
            long fairPrice,
            long sellPrice,
            long buyPrice) {}

    /** 시장 체결가로부터 상태별 밸류에이션을 산출한다. */
    public static PriceValuation fromMarketPrice(String setNumber, long marketPrice) {
        List<ConditionValuation> list = new ArrayList<>();
        for (SetCondition condition : SetCondition.values()) {
            long fair = Math.round(marketPrice * condition.factor());
            long sell = Math.round(fair * SELL_SPREAD);
            long buy = Math.round(fair * BUY_SPREAD);
            list.add(new ConditionValuation(condition, condition.depreciationPct(), fair, sell, buy));
        }
        return new PriceValuation(setNumber, marketPrice, List.copyOf(list));
    }
}
