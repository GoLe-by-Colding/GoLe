package com.gole.api.pricing.domain.model;

/**
 * 시세 산정용 상품 상태 등급과 상태별 감가 계수.
 *
 * <p>시장 체결가(최근 체결가)를 미개봉 새상품 기준으로 보고, 상태가 낮아질수록
 * 감가 계수를 곱해 상태별 공정 시세를 산출한다. listing 컨텍스트의 {@code ItemCondition}
 * 키(new_sealed/used_complete/used_incomplete)와 동일한 문자열을 노출해 프론트/매물과 정합한다.
 */
public enum SetCondition {
    NEW_SEALED("new_sealed", 1.00),
    USED_COMPLETE("used_complete", 0.78),
    USED_INCOMPLETE("used_incomplete", 0.55);

    private final String key;
    private final double factor;

    SetCondition(String key, double factor) {
        this.key = key;
        this.factor = factor;
    }

    /** 프론트/매물과 공유하는 상태 키. */
    public String key() {
        return key;
    }

    /** 새상품 대비 시세 계수(1.0 = 감가 없음). */
    public double factor() {
        return factor;
    }

    /** 감가율(%) = (1 - factor) * 100. */
    public int depreciationPct() {
        return (int) Math.round((1.0 - factor) * 100);
    }
}
