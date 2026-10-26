package com.gole.api.design.domain.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 기본 제공 마스코트 — 지금까지 그린 고래 마크의 키와 이름. (mascot-assets R1)
 *
 * <p>그림은 웹(`shared/ui/logo/presets/`)이 가진다. 과거 적용을 읽는 전체 목록과 새로 적용할 수 있는 목록을 구분한다.
 * 키나 선택 가능 여부를 바꾸면 웹 프리셋 목록과 `@gole/core/mascot`의 메타데이터도 함께 고친다.
 */
public final class MascotPresets {
    private MascotPresets() {}

    /** 아무것도 적용하지 않았을 때의 마스코트 — 2026-10-21 미소 브릭 고래. */
    public static final String DEFAULT_KEY = "side-brick-soft";

    public static final String DEFAULT_NAME = "미소 브릭 고래";

    private static final Map<String, String> NAMES;
    private static final Set<String> EXCLUDED_KEYS = Set.of("first-sketch", "chubby", "kawaii-brick", "three-studs");

    static {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("side-brick-assembled", "조립 브릭 고래");
        names.put("side-brick-pocket", "포켓 브릭 고래");
        names.put("side-brick-plate", "플레이트 브릭 고래");
        names.put(DEFAULT_KEY, DEFAULT_NAME);
        names.put("side-brick-mellow", "말랑 브릭 고래");
        names.put("side-brick", "옆모습 브릭 고래");
        names.put("side-brick-original", "원본 브릭 고래");
        names.put("baby-round", "둥근 아기 고래");
        names.put("front-stud-head", "정면 스터드 머리 고래");
        names.put("tail-up", "꼬리 든 고래");
        names.put("kawaii-brick", "카와이 브릭 고래");
        names.put("chubby", "통통 고래");
        names.put("flat-silhouette", "한 획 플랫 고래");
        names.put("blocky", "블로키 브릭 고래");
        names.put("three-studs", "스터드 세 개 고래");
        names.put("fountain", "분수 고래");
        names.put("first-sketch", "첫 고래");
        NAMES = java.util.Collections.unmodifiableMap(names);
    }

    /** 과거 적용·이력을 포함한 전체 키 목록. */
    public static final List<String> KEYS = List.copyOf(NAMES.keySet());

    /** 관리자 화면에 노출하고 새로 적용할 수 있는 키 목록. */
    public static final List<String> SELECTABLE_KEYS =
            KEYS.stream().filter(MascotPresets::isSelectablePreset).toList();

    public static boolean isPreset(String id) {
        return id != null && NAMES.containsKey(id);
    }

    public static boolean isSelectablePreset(String id) {
        return isPreset(id) && !EXCLUDED_KEYS.contains(id);
    }

    public static Optional<String> nameOf(String key) {
        return Optional.ofNullable(key == null ? null : NAMES.get(key));
    }
}
