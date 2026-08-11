package com.gole.api.pricing.domain.model;

/**
 * 상태별 공정 시세를 무엇에 근거해 냈는지. 값이 아니라 <b>근거의 강도</b>를 나타낸다.
 *
 * <p>사용자에게 "이 숫자를 얼마나 믿어도 되는지"를 그대로 노출하기 위한 것이다.
 * 숫자만 보여주고 근거를 감추면, 표본 1건짜리 추정과 실거래 50건이 같아 보인다.
 */
public enum ValuationBasis {
    /** 해당 등급의 실제 체결가가 충분해 그 중앙값을 그대로 썼다. 가장 강한 근거. */
    GRADE("grade"),
    /** 등급 표본이 모자라, 한 단계 굵은 {@link ConditionGroup} 체결가를 앵커로 환산했다. */
    GROUP("group"),
    /** 체결 표본이 없어 미개봉 시세에 감가 계수만 곱했다. 가장 약한 근거. */
    MODEL("model");

    private final String key;

    ValuationBasis(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /** 실제 체결 데이터에 기반했는지. {@link #MODEL}만 아니면 참. */
    public boolean isReal() {
        return this != MODEL;
    }
}
