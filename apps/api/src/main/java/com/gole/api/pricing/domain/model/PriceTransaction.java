package com.gole.api.pricing.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 체결 거래 기록. 플랫폼 결제 주문이 구매확정될 때 카탈로그 세트 기준으로 적재된다. (요구사항 9.1)
 * 불변 값(이벤트). 상품 상태(condition)는 상태별 시세 산정에 사용한다(미지정 시 미개봉으로 간주).
 */
public record PriceTransaction(String setNumber, long price, int quantity, Instant executedAt, SetCondition condition) {

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
        if (condition == null) {
            condition = SetCondition.NEW_SEALED;
        }
    }

    /** 상태 미지정(레거시) 체결 — 미개봉 새상품으로 간주. */
    public PriceTransaction(String setNumber, long price, int quantity, Instant executedAt) {
        this(setNumber, price, quantity, executedAt, SetCondition.NEW_SEALED);
    }
}
