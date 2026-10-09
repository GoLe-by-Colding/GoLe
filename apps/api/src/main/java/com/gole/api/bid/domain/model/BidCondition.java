package com.gole.api.bid.domain.model;

import java.util.Locale;
import java.util.Optional;

/**
 * 입찰 상태 키. 매물 상태 키와 같은 값을 쓰되 listing 도메인을 가져오지 않는다. (buy-bids D1)
 *
 * <p>선언 순서가 호가창의 표시 순서다 — 좋은 상태부터.
 */
public enum BidCondition {
    NEW_SEALED("new_sealed"),
    LIKE_NEW("like_new"),
    USED_GOOD("used_good"),
    USED_FAIR("used_fair"),
    DAMAGED("damaged");

    private final String key;

    BidCondition(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /** 소문자 키 또는 열거형 이름. 모르는 값은 비어 있음 — 임의의 상태로 흡수하지 않는다. */
    public static Optional<BidCondition> fromKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (BidCondition condition : values()) {
            if (condition.key.equals(normalized)
                    || condition.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return Optional.of(condition);
            }
        }
        return Optional.empty();
    }
}
