package com.gole.api.pricing.domain.model;

import java.util.List;

/**
 * 상태별 시세 밸류에이션. 시장 체결가(미개봉 기준)에서 상태별 공정 시세를 산출하고,
 * 각 상태마다 즉시판매(매도)·즉시구매(매수) 호가를 스프레드로 산출한다. (KREAM식 호가)
 *
 * <p>상태별 공정가는 근거가 강한 순서로 3단계를 밟는다 — 등급 실측 → 그룹 실측 환산 → 감가 모델.
 * 어느 단계를 썼는지는 {@link ValuationBasis}로, 표본 수는 {@code sampleCount}로 노출한다.
 */
public record PriceValuation(String setNumber, long marketPrice, List<ConditionValuation> conditions) {

    /** 즉시판매(매도) 스프레드 — 공정가보다 약간 낮게. */
    public static final double SELL_SPREAD = 0.96;
    /** 즉시구매(매수) 스프레드 — 공정가보다 약간 높게. */
    public static final double BUY_SPREAD = 1.05;

    /**
     * @param sampleCount     공정가 산출에 실제로 쓰인 체결 건수. 모델 폴백이면 0.
     * @param basedOnRealData 체결 데이터 기반 여부. {@code basis.isReal()}과 같다.
     */
    public record ConditionValuation(
            SetCondition condition,
            ValuationBasis basis,
            int depreciationPct,
            long fairPrice,
            long sellPrice,
            long buyPrice,
            int sampleCount,
            boolean basedOnRealData) {}

    /** 감가 모델 기반(체결 표본 없음). 미개봉 시세에 등급 계수만 곱한다. */
    public static ConditionValuation model(SetCondition condition, long marketPrice) {
        long fair = Math.round(marketPrice * condition.factor());
        return spread(condition, ValuationBasis.MODEL, fair, condition.depreciationPct(), 0);
    }

    /** 해당 등급의 실제 체결가 기반. 가장 강한 근거. */
    public static ConditionValuation grade(SetCondition condition, long marketPrice, long fairPrice, int sampleCount) {
        return spread(condition, ValuationBasis.GRADE, fairPrice, depreciation(marketPrice, fairPrice), sampleCount);
    }

    /**
     * 그룹 체결가를 앵커로 환산. 등급 표본이 모자랄 때 쓴다.
     *
     * <p>{@code 등급가 = 그룹중앙값 × 등급계수 / 그룹대표계수}. 그룹 안에서의 상대 위치만
     * 모델로 보정하므로, 미개봉 시세에서 통째로 외삽하는 {@link #model}보다 앵커가 가깝다.
     *
     * <p>⚠️ {@code groupReference}는 <b>그 그룹의 등급 계수 평균이 아니라, 앵커를 만든 표본
     * 자체에서 뽑아야 한다.</b> {@code groupMedian}은 표본 가중 통계라 표본이 한 등급에 쏠리면
     * 그 등급 쪽으로 끌려간다. 그런데 그룹 폴백은 <b>정의상</b> 대상 등급의 표본이 얇을 때만
     * 타므로, 쏠린 표본이 예외가 아니라 기본 상황이다. 여기에 등급 계수의 단순 평균을 기준으로
     * 쓰면 두 통계의 기준점이 어긋나 체계적으로 편향된다 — 예를 들어 INCOMPLETE 그룹이
     * USED_FAIR 체결로만 차 있을 때 DAMAGED가 약 16% 비싸게 나온다.
     *
     * @param groupMedian    그룹 체결가의 중앙값
     * @param groupReference 그 중앙값에 대응하는 감가 계수. 0 이하면 감가 모델로 물러난다
     * @param sampleCount    그룹 체결 건수
     */
    public static ConditionValuation group(
            SetCondition condition, long marketPrice, long groupMedian, double groupReference, int sampleCount) {
        long fair = groupReference <= 0
                ? Math.round(marketPrice * condition.factor())
                : Math.round(groupMedian * (condition.factor() / groupReference));
        return spread(condition, ValuationBasis.GROUP, fair, depreciation(marketPrice, fair), sampleCount);
    }

    /** 시장가 대비 감가율(%). 시장가가 없거나 공정가가 더 높으면 0. */
    private static int depreciation(long marketPrice, long fairPrice) {
        if (marketPrice <= 0) {
            return 0;
        }
        return Math.max(0, (int) Math.round((1.0 - (double) fairPrice / marketPrice) * 100));
    }

    private static ConditionValuation spread(
            SetCondition condition, ValuationBasis basis, long fair, int dep, int sampleCount) {
        long sell = Math.round(fair * SELL_SPREAD);
        long buy = Math.round(fair * BUY_SPREAD);
        return new ConditionValuation(condition, basis, dep, fair, sell, buy, sampleCount, basis.isReal());
    }
}
