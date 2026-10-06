package com.gole.api.promotion.domain.model;

import java.util.regex.Pattern;

/**
 * 게이트웨이 호출 한 번의 사용량. 실패한 호출도 토큰을 쓰므로 함께 남긴다.
 *
 * @param engine claude·codex 같은 CLI 이름
 * @param model 실제로 쓴 모델 이름. 알 수 없으면 null
 * @param inputTokens 캐시 적중분을 포함한 전체 입력 토큰
 * @param cachedInputTokens 그중 캐시에서 읽은 토큰
 * @param costUsd API 키였다면 냈을 환산 금액. 구독 실행이라 실제 청구액이 아니다. 모르면 null
 */
public record ModelCall(
        String engine,
        String model,
        boolean ok,
        long inputTokens,
        long cachedInputTokens,
        long outputTokens,
        Double costUsd,
        Long durationMs) {

    private static final Pattern ENGINE = Pattern.compile("[a-z][a-z0-9-]{0,15}");
    private static final Pattern MODEL = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:,-]{0,255}");

    public ModelCall {
        if (engine == null || !ENGINE.matcher(engine).matches()) {
            throw new IllegalArgumentException("engine must be a short lowercase name");
        }
        if (model != null && !MODEL.matcher(model).matches()) {
            throw new IllegalArgumentException("model has unexpected characters");
        }
        if (inputTokens < 0 || cachedInputTokens < 0 || outputTokens < 0) {
            throw new IllegalArgumentException("token counts must not be negative");
        }
        if (cachedInputTokens > inputTokens) {
            throw new IllegalArgumentException("cached input cannot exceed input");
        }
        if (costUsd != null && (costUsd.isNaN() || costUsd < 0)) {
            throw new IllegalArgumentException("costUsd must not be negative");
        }
        if (durationMs != null && durationMs < 0) {
            throw new IllegalArgumentException("durationMs must not be negative");
        }
    }
}
