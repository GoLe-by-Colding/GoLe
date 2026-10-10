package com.gole.api.promotion.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 스크린샷 한 장의 설명표. 게시물의 {@code mediaUrls} 와 같은 순서로 1:1 대응한다.
 *
 * <p>게시 이미지가 실제 화면을 AI 로 다듬은 것이면 {@code originalUrl} 에 다듬기 전 원본 캡처가,
 * {@code edit} 에 다듬기 지시문이 남는다. 검토자는 둘을 나란히 놓고 글자·숫자가 바뀌지 않았는지 본다.
 *
 * @param label 에이전트가 붙인 이름 (예: "필터 열린 목록")
 * @param route 찍은 화면 경로 (예: "/market")
 * @param actions 찍기 전에 한 조작을 사람이 읽을 문장으로 (없으면 빈 문자열)
 * @param originalUrl 다듬기 전 원본 캡처의 공개 경로. 원본을 그대로 올렸으면 null
 * @param edit 다듬기 지시문. 원본을 그대로 올렸으면 null
 */
public record PromotionCapture(
        String label,
        String route,
        String actions,
        CaptureDataSource dataSource,
        Instant capturedAt,
        String originalUrl,
        String edit) {

    private static final int MAX_TEXT = 300;
    private static final int MAX_EDIT = 6000;

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
        // 외부 주소를 검토 화면에 띄우지 않는다 — 우리 미디어 경로만 받는다.
        if (originalUrl != null && (!originalUrl.startsWith("/") || originalUrl.startsWith("//"))) {
            throw new IllegalArgumentException("originalUrl must be a site path");
        }
        edit = edit == null || edit.isBlank() ? null : edit.strip();
        if (edit != null && edit.length() > MAX_EDIT) {
            throw new IllegalArgumentException("edit too long");
        }
        if ((originalUrl == null) != (edit == null)) {
            throw new IllegalArgumentException("originalUrl and edit go together");
        }
    }

    /** 원본을 그대로 올린 사진. */
    public PromotionCapture(
            String label, String route, String actions, CaptureDataSource dataSource, Instant capturedAt) {
        this(label, route, actions, dataSource, capturedAt, null, null);
    }

    public PromotionCapture withOriginal(String originalUrl, String edit) {
        return new PromotionCapture(label, route, actions, dataSource, capturedAt, originalUrl, edit);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank() || value.strip().length() > MAX_TEXT) {
            throw new IllegalArgumentException(name + " must be 1.." + MAX_TEXT + " characters");
        }
        return value.strip();
    }
}
