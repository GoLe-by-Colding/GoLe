package com.gole.api.pricing.domain.model;

import java.util.Locale;

/**
 * 체결가의 증빙 출처. 시장 통계에 포함할 수 있는지는 이름이 아니라 정책에서 결정한다.
 *
 * <p>기존 {@code source=null} 문서는 출처를 증명할 수 없으므로 반드시
 * {@link #LEGACY_UNVERIFIED}로 읽는다. 샘플과 실제 체결이 섞인 과거 데이터를 임의로
 * 플랫폼 결제로 승격하지 않는다.
 */
public enum PriceTransactionSource {
    PLATFORM_PAYMENT("platform_payment"),
    PLATFORM_TEST("platform_test"),
    DIRECT_TRADE("direct_trade"),
    DEMO_SEED("demo_seed"),
    LEGACY_UNVERIFIED("legacy_unverified");

    private final String key;

    PriceTransactionSource(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static PriceTransactionSource fromKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return LEGACY_UNVERIFIED;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (PriceTransactionSource source : values()) {
            if (source.key.equals(normalized) || source.name().equalsIgnoreCase(normalized)) {
                return source;
            }
        }
        return LEGACY_UNVERIFIED;
    }
}
