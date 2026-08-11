package com.gole.api.listing.domain.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 상품 상태 등급. (요구사항 5.4, condition-disclosure R1)
 *
 * <p>기존 3단계(new_sealed/used_complete/used_incomplete)에서 5단계로 확장했다.
 * 등급은 <b>고지(disclosure) 축</b>이다 — 구매자가 보고 판단하는 정보라 잘게 나눌수록 좋다.
 * 시세 <b>집계</b> 축은 표본 수에 따라 더 굵게 묶이며, 그쪽은 pricing 컨텍스트의
 * {@code SetCondition}/{@code ConditionGroup}이 담당한다.
 *
 * <p>DB에 남아 있는 레거시 값은 저장된 문서를 일괄 변환하지 않고 읽기·조회 시점에 흡수한다.
 * 읽기는 {@link #fromKey(String)}, 조회는 {@link #storageNames()}가 맡는다.
 */
public enum ItemCondition {
    /** 새상품(미개봉). */
    NEW_SEALED("new_sealed"),
    /** 거의 새것 — 개봉했으나 전시/보관만 한 상태. */
    LIKE_NEW("like_new"),
    /** 중고-양호 — 사용 흔적이 적고 구성이 온전함. */
    USED_GOOD("used_good"),
    /** 중고-사용감 있음. */
    USED_FAIR("used_fair"),
    /** 하자/손상 있음. */
    DAMAGED("damaged");

    /** 3단계 시절 저장값 → 새 등급. (condition-disclosure 마이그레이션 매핑) */
    private static final Map<String, ItemCondition> LEGACY_KEYS = legacyKeyMap();

    private final String key;

    ItemCondition(String key) {
        this.key = key;
    }

    private static Map<String, ItemCondition> legacyKeyMap() {
        Map<String, ItemCondition> map = new LinkedHashMap<>();
        map.put("used_complete", USED_GOOD);
        map.put("used_incomplete", USED_FAIR);
        return Map.copyOf(map);
    }

    /** 프론트·API와 공유하는 소문자 키. */
    public String key() {
        return key;
    }

    /**
     * 이 등급으로 조회해야 할 저장값 전체 — 현재 이름 + 이 등급으로 매핑되는 레거시 이름.
     *
     * <p>리스팅 문서는 enum 이름(대문자)으로 저장된다. 새 이름으로만 필터하면 3단계 시절
     * 매물이 검색에서 통째로 사라지므로, 상태 필터는 이 목록으로 조회한다.
     */
    public List<String> storageNames() {
        List<String> names = new ArrayList<>();
        names.add(name());
        LEGACY_KEYS.forEach((legacy, mapped) -> {
            if (mapped == this) {
                names.add(legacy.toUpperCase());
            }
        });
        return List.copyOf(names);
    }

    /**
     * 상태 키 → 등급. 대소문자와 레거시 값을 흡수하되, 모르는 값이면 비어 있다.
     *
     * <p><b>입력 경로(API 파라미터)</b>는 이쪽을 쓴다. 오타를 조용히 특정 등급으로 바꿔치기하면
     * 사용자는 필터가 먹은 줄 알지만 실제로는 엉뚱한 결과를 보게 된다.
     */
    public static Optional<ItemCondition> parseKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        String normalized = key.trim().toLowerCase();
        for (ItemCondition c : values()) {
            if (c.key.equals(normalized) || c.name().toLowerCase().equals(normalized)) {
                return Optional.of(c);
            }
        }
        return Optional.ofNullable(LEGACY_KEYS.get(normalized));
    }

    /**
     * 상태 키 → 등급. 모르는 값은 {@link #USED_GOOD}으로 둔다.
     *
     * <p><b>읽기 경로(DB 문서)</b>는 이쪽을 쓴다. 저장된 값 하나 때문에 매물 조회 전체가
     * 실패하면 안 되기 때문이다. 중고 마켓에서 기본값을 미개봉으로 두면 실제보다 좋은 상태로
     * 보이므로, 모를 때는 새상품이 아닌 쪽으로 기운다.
     */
    public static ItemCondition fromKey(String key) {
        return parseKey(key).orElse(USED_GOOD);
    }
}
