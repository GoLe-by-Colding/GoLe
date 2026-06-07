package com.gole.api.pricing.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 체결 거래 기록. 에스크로 주문이 완료될 때 카탈로그 세트 기준으로 적재된다. (요구사항 9.1)
 * 불변 값(이벤트).
 */
public record PriceTransaction(
        String setNumber, long price, int quantity, Instant executedAt) {

    public PriceTransaction {
        if (setNumber == null || setNumber.isBlank()) {
            throw new IllegalArgumentException("setNumber must not be blank");
        }
        if (price < 0) {
            throw new IllegalArgumentException("price must be >= 0");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0");
        }
        Objects.requireNonNull(executedAt, "executedAt");
    }
}
