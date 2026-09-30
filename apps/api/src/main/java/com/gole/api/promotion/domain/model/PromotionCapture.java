package com.gole.api.promotion.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 스크린샷 한 장의 설명표. 게시물의 {@code mediaUrls} 와 같은 순서로 1:1 대응한다.
 *
 * @param label 에이전트가 붙인 이름 (예: "필터 열린 목록")
 * @param route 찍은 화면 경로 (예: "/market")
 * @param actions 찍기 전에 한 조작을 사람이 읽을 문장으로 (없으면 빈 문자열)
 */
public record PromotionCapture(
        String label, String route, String actions, CaptureDataSource dataSource, Instant capturedAt) {

    private static final int MAX_TEXT = 300;

    public PromotionCapture {
        label = requireText(label, "label");
        route = requireText(route, "route");
        if (!route.startsWith("/")) {
            throw new IllegalArgumentException("route must start with /");
        }
        actions = actions == null ? "" : actions.strip();
        if (actions.length() > MAX_TEXT) {
            throw new IllegalArgumentException("actions too long");
        }
        Objects.requireNonNull(dataSource, "dataSource");
        Objects.requireNonNull(capturedAt, "capturedAt");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank() || value.strip().length() > MAX_TEXT) {
            throw new IllegalArgumentException(name + " must be 1.." + MAX_TEXT + " characters");
        }
        return value.strip();
    }
}
