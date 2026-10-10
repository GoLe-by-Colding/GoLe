package com.gole.api.design.domain.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 기본 제공 마스코트 — 지금까지 그린 고래 마크 12벌의 키와 이름. (mascot-assets R1)
 *
 * <p>그림은 웹(`shared/ui/logo/presets/`)이 가진다. 서버는 적용 요청이 아는 키인지 판정하고, 이력에 남길 이름을 안다.
 * 키를 더하거나 지우면 웹 프리셋 목록과 `@gole/core/mascot`의 메타데이터도 함께 고친다.
 */
public final class MascotPresets {
    private MascotPresets() {}

    /** 아무것도 적용하지 않았을 때의 마스코트 — 2026-10-09 옆모습 브릭 고래. */
    public static final String DEFAULT_KEY = "side-brick";

    public static final String DEFAULT_NAME = "옆모습 브릭 고래";

    private static final Map<String, String> NAMES;

    static {
        Map<String, String> names = new LinkedHashMap<>();
        names.put(DEFAULT_KEY, DEFAULT_NAME);
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

    /** 화면 순서 그대로의 키 목록. */
    public static final List<String> KEYS = List.copyOf(NAMES.keySet());

    public static boolean isPreset(String id) {
        return id != null && NAMES.containsKey(id);
    }

    public static Optional<String> nameOf(String key) {
        return Optional.ofNullable(key == null ? null : NAMES.get(key));
    }
}
