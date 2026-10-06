package com.gole.api.promotion.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 홍보 초안 에이전트 실행 한 번의 원장(promotion-review D23). 실행 1건 = 1줄이고 같은
 * {@code runKey} 로는 한 번만 남는다.
 *
 * <p>이 기록은 관리자만 본다. 공개 Actions 로그에는 결과와 {@link RunReasonCode} 만 나가고,
 * 모델이 쓴 사유 원문은 {@code detail} 에만 남는다. 모델 출력은 diff·화면 글자로 조작될 수 있는
 * 입력이라 길이를 자르고 제어문자를 지운 뒤 저장한다.
 *
 * @param runKey 실행 식별자. Actions 는 {@code gh-<run_id>-<attempt>}
 * @param detail 사유 원문(모델이 쓴 건너뛴 이유, 내부 오류 요약). 없으면 null
 * @param calls 게이트웨이 호출별 사용량
 */
public record PromotionRun(
        String id,
        String runKey,
        PromotionCategory category,
        String sourceCommitSha,
        RunOutcome outcome,
        RunReasonCode reasonCode,
        String detail,
        String promotionPostId,
        String agentSha,
        String runUrl,
        List<ModelCall> calls,
        Instant recordedAt) {

    public static final int MAX_DETAIL = 300;
    public static final int MAX_CALLS = 20;
    private static final int MAX_RUN_URL = 300;
    private static final Pattern RUN_KEY = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");
    private static final Pattern COMMIT_SHA = Pattern.compile("[0-9a-f]{40}");
    private static final Pattern AGENT_SHA = Pattern.compile("[0-9a-f]{7,40}");
    private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    public PromotionRun {
        Objects.requireNonNull(id, "id");
        if (runKey == null || !RUN_KEY.matcher(runKey).matches()) {
            throw new IllegalArgumentException("runKey must be lowercase letters, digits and dashes");
        }
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(reasonCode, "reasonCode");
        Objects.requireNonNull(recordedAt, "recordedAt");
        if (sourceCommitSha != null && !COMMIT_SHA.matcher(sourceCommitSha).matches()) {
            throw new IllegalArgumentException("sourceCommitSha must be a 40-char lowercase hex");
        }
        if (agentSha != null && !AGENT_SHA.matcher(agentSha).matches()) {
            throw new IllegalArgumentException("agentSha must be a lowercase hex commit");
        }
        if (runUrl != null && (!runUrl.startsWith("https://github.com/") || runUrl.length() > MAX_RUN_URL)) {
            throw new IllegalArgumentException("runUrl must be a GitHub Actions link");
        }
        if ((outcome == RunOutcome.SUBMITTED) != (reasonCode == RunReasonCode.SUBMITTED)) {
            throw new IllegalArgumentException("SUBMITTED outcome and reason go together");
        }
        if (outcome == RunOutcome.SUBMITTED && (promotionPostId == null || promotionPostId.isBlank())) {
            throw new IllegalArgumentException("a submitted run must name its draft");
        }
        detail = cleanDetail(detail);
        calls = calls == null ? List.of() : List.copyOf(calls);
        if (calls.size() > MAX_CALLS) {
            throw new IllegalArgumentException("too many model calls");
        }
    }

    /** 제어문자를 지우고 공백을 하나로 모은 뒤 {@value #MAX_DETAIL}자로 자른다. 비면 null. */
    static String cleanDetail(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = SPACES.matcher(CONTROL.matcher(raw).replaceAll(" "))
                .replaceAll(" ")
                .strip();
        if (cleaned.isEmpty()) {
            return null;
        }
        if (cleaned.codePointCount(0, cleaned.length()) <= MAX_DETAIL) {
            return cleaned;
        }
        // 글자 단위로 자른다 — 이모지 같은 서로게이트 쌍을 반으로 가르지 않는다.
        return cleaned.substring(0, cleaned.offsetByCodePoints(0, MAX_DETAIL));
    }
}
