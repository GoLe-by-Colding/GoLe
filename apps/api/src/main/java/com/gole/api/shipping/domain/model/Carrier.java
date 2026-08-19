package com.gole.api.shipping.domain.model;

import java.util.Locale;
import java.util.Optional;

/**
 * 지원 택배사. (shipping-and-fees R1.1)
 *
 * <p>{@code trackerId}는 Delivery Tracker API(tracker.delivery)의 캐리어 식별자다.
 * 실 트래커 어댑터가 이 값을 그대로 쓰므로, 새 택배사를 추가할 때는 트래커가
 * 지원하는 식별자인지 먼저 확인한다.
 */
public enum Carrier {
    CJ_LOGISTICS("kr.cjlogistics", "CJ대한통운"),
    POST_OFFICE("kr.epost", "우체국택배"),
    HANJIN("kr.hanjin", "한진택배"),
    LOTTE("kr.lotte", "롯데택배"),
    LOGEN("kr.logen", "로젠택배");

    private final String trackerId;
    private final String label;

    Carrier(String trackerId, String label) {
        this.trackerId = trackerId;
        this.label = label;
    }

    /** 열거형 이름(대소문자 무관) 또는 트래커 식별자로 조회한다. */
    public static Optional<Carrier> fromKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        String normalized = key.trim();
        for (Carrier carrier : values()) {
            if (carrier.name().equalsIgnoreCase(normalized) || carrier.trackerId.equals(normalized)) {
                return Optional.of(carrier);
            }
        }
        return Optional.empty();
    }

    public String trackerId() {
        return trackerId;
    }

    public String label() {
        return label;
    }

    /** API 응답·저장에 쓰는 소문자 키. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
