package com.gole.api.pricing.domain.model;

import java.util.Arrays;
import java.util.List;

/**
 * 시세 <b>집계</b> 단위. 등급({@link SetCondition})보다 굵다.
 *
 * <p>왜 나누는가 — 등급을 잘게 쪼갤수록 매물 고지는 정확해지지만, 등급별 체결 표본은 그만큼
 * 흩어진다. 표본이 흩어지면 등급별 실측 중앙값을 낼 수 없어 오히려 감가 모델로 후퇴한다.
 * 그래서 <b>고지 축(5등급)</b>과 <b>집계 축(3그룹)</b>을 분리하고, 등급 표본이 모자랄 때
 * 한 단계 굵은 그룹 표본으로 받친다. 데이터가 쌓이면 등급 단위 실측으로 자연히 올라선다.
 *
 * <p>그룹 경계는 기존 3단계(new_sealed/used_complete/used_incomplete)와 일치시켰다.
 * 레거시 체결 데이터가 그대로 올바른 그룹에 떨어진다.
 */
public enum ConditionGroup {
    /** 미개봉. */
    SEALED("sealed"),
    /** 개봉했으나 구성이 온전함. */
    COMPLETE("complete"),
    /** 부품 누락·하자 등 온전하지 않음. */
    INCOMPLETE("incomplete");

    private final String key;

    ConditionGroup(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /**
     * 이 그룹에 속한 등급들.
     *
     * <p>{@link SetCondition}이 소속 그룹을 들고 있고 여기서 역으로 모은다. 양쪽에 명단을
     * 중복해 두면 반드시 갈라지므로, 소속 정의는 {@link SetCondition} 한 곳에만 둔다.
     */
    public List<SetCondition> members() {
        return Arrays.stream(SetCondition.values())
                .filter(c -> c.group() == this)
                .toList();
    }

    /**
     * 그룹 대표 감가 계수 — 소속 등급 계수의 평균.
     *
     * <p>그룹 표본의 중앙값은 "이 그룹의 전형적인 물건" 가격이다. 그 전형값이 어느 계수에
     * 해당하는지가 이 값이고, 특정 등급으로 환산할 때 기준점이 된다.
     * {@code 등급가 = 그룹중앙값 × 등급계수 / 그룹대표계수}
     */
    public double referenceFactor() {
        return members().stream().mapToDouble(SetCondition::factor).average().orElse(1.0);
    }
}
