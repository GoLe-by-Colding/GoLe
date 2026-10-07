package com.gole.api.promotion.domain.model;

/**
 * 초안이 어디서 어떻게 나왔나 — 검토자가 판단 근거를 따라가는 데 쓴다.
 *
 * @param releaseTitle 출처 릴리스 제목. 서비스 홍보는 null.
 * @param rationale 에이전트가 이 화면·주제를 고른 이유.
 * @param runUrl 이 초안을 만든 실행 로그. 관리자 화면이 링크로 여므로 GitHub https 주소만 받는다.
 */
public record PromotionProvenance(String releaseTitle, String rationale, String runUrl) {

    private static final int MAX_TEXT = 500;
    private static final String RUN_URL_PREFIX = "https://github.com/";

    public PromotionProvenance {
        releaseTitle = optional(releaseTitle, "releaseTitle");
        rationale = optional(rationale, "rationale");
        runUrl = optional(runUrl, "runUrl");
        // javascript: 같은 스킴이 관리자 화면의 링크로 들어가지 않게 한다.
        if (runUrl != null && !runUrl.startsWith(RUN_URL_PREFIX)) {
            throw new IllegalArgumentException("runUrl must start with " + RUN_URL_PREFIX);
        }
    }

    private static String optional(String value, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String stripped = value.strip();
        if (stripped.length() > MAX_TEXT) {
            throw new IllegalArgumentException(name + " too long");
        }
        return stripped;
    }
}
