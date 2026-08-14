package com.gole.api.pricing.domain.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 시세 산정용 상품 상태 등급과 상태별 감가 계수.
 *
 * <p>시장 체결가(미개봉 최근 체결가)를 기준으로 보고, 상태가 낮아질수록 감가 계수를 곱해
 * 상태별 공정 시세를 산출한다. listing 컨텍스트의 {@code ItemCondition}과 <b>같은 키</b>를
 * 노출해 매물·프론트와 정합한다.
 *
 * <p>계수는 등급별 실측 체결가가 쌓이기 전까지 쓰는 <b>폴백</b>이다. 표본이 충분해지면
 * {@code PricingService}가 실측 중앙값으로 갈아탄다. 계수 자체가 정답인 것은 아니다.
 *
 * <p>각 등급은 {@link ConditionGroup}에 속한다. 등급 표본이 모자랄 때 그룹 표본으로 받치기
 * 위한 것으로, 소속 정의는 이 파일 한 곳에만 둔다.
 */
public enum SetCondition {
    NEW_SEALED("new_sealed", 1.00, ConditionGroup.SEALED),
    LIKE_NEW("like_new", 0.88, ConditionGroup.COMPLETE),
    USED_GOOD("used_good", 0.78, ConditionGroup.COMPLETE),
    USED_FAIR("used_fair", 0.62, ConditionGroup.INCOMPLETE),
    DAMAGED("damaged", 0.45, ConditionGroup.INCOMPLETE);

    /**
     * 3단계 시절 저장된 키 → 새 등급. (condition-disclosure 마이그레이션)
     *
     * <p>저장된 문서를 일괄 변환하지 않고 읽기·조회 시점에 흡수한다. 그래서 이 표는
     * {@link #fromKey}(읽기)와 {@link #storageKeys()}(조회) 양쪽이 함께 쓴다.
     */
    private static final Map<String, SetCondition> LEGACY_KEYS = legacyKeyMap();

    private final String key;
    private final double factor;
    private final ConditionGroup group;

    SetCondition(String key, double factor, ConditionGroup group) {
        this.key = key;
        this.factor = factor;
        this.group = group;
    }

    private static Map<String, SetCondition> legacyKeyMap() {
        Map<String, SetCondition> map = new LinkedHashMap<>();
        map.put("used_complete", USED_GOOD);
        map.put("used_incomplete", USED_FAIR);
        return Map.copyOf(map);
    }

    /** 프론트/매물과 공유하는 상태 키. */
    public String key() {
        return key;
    }

    /** 새상품 대비 시세 계수(1.0 = 감가 없음). 실측 표본이 없을 때의 폴백. */
    public double factor() {
        return factor;
    }

    /** 시세 집계 그룹. */
    public ConditionGroup group() {
        return group;
    }

    /** 감가율(%) = (1 - factor) * 100. */
    public int depreciationPct() {
        return (int) Math.round((1.0 - factor) * 100);
    }

    /**
     * 이 등급으로 조회해야 할 저장 키 전체 — 현재 키 + 이 등급으로 매핑되는 레거시 키.
     *
     * <p>새 키로만 조회하면 3단계 시절 체결 이력이 통째로 빠진다. 표본이 사라지면 실측이
     * 모델 폴백으로 후퇴하므로, 조회는 반드시 이 목록으로 한다.
     */
    public List<String> storageKeys() {
        List<String> keys = new ArrayList<>();
        keys.add(key);
        LEGACY_KEYS.forEach((legacy, mapped) -> {
            if (mapped == this) {
                keys.add(legacy);
            }
        });
        return List.copyOf(keys);
    }

    /**
     * 상태 키 → enum. 레거시 키(used_complete/used_incomplete)를 새 등급으로 매핑한다.
     *
     * <p>{@code null}/미지정은 미개봉으로 간주한다. 상태를 태깅하지 않던 시절의 체결가는
     * 헤드라인 시세(미개봉 기준)로 쓰이던 값이라 기존 해석을 유지한다.
     */
    public static SetCondition fromKey(String key) {
        if (key == null || key.isBlank()) {
            return NEW_SEALED;
        }
        String normalized = key.trim().toLowerCase();
        for (SetCondition c : values()) {
            if (c.key.equals(normalized) || c.name().toLowerCase().equals(normalized)) {
                return c;
            }
        }
        return LEGACY_KEYS.getOrDefault(normalized, NEW_SEALED);
    }
}
