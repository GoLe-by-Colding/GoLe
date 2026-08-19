package com.gole.api.order.domain.model;

import java.util.Locale;
import java.util.Optional;

/** 분쟁 제기 사유. (shipping-and-fees R4.1) */
public enum DisputeReason {
    NOT_SHIPPED("미발송"),
    NOT_ARRIVED("미도착"),
    ITEM_MISMATCH("상품 불일치"),
    DAMAGED("파손");

    private final String label;

    DisputeReason(String label) {
        this.label = label;
    }

    public static Optional<DisputeReason> fromKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        for (DisputeReason reason : values()) {
            if (reason.name().equalsIgnoreCase(key.trim())) {
                return Optional.of(reason);
            }
        }
        return Optional.empty();
    }

    public String label() {
        return label;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
