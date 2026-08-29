package com.gole.api.pricing.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 체결 거래 기록. 플랫폼 결제 주문이 구매확정될 때 카탈로그 세트 기준으로 적재된다. (요구사항 9.1)
 * 불변 값(이벤트). 상품 상태(condition)는 상태별 시세 산정에 사용한다(미지정 시 미개봉으로 간주).
 */
public record PriceTransaction(
        String setNumber,
        long price,
        int quantity,
        Instant executedAt,
        SetCondition condition,
        PriceTransactionSource source,
        String sourceReference) {

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
        if (source == null) {
            source = PriceTransactionSource.LEGACY_UNVERIFIED;
        }
        sourceReference = normalizeReference(sourceReference);
        if ((source == PriceTransactionSource.PLATFORM_PAYMENT
                        || source == PriceTransactionSource.PLATFORM_TEST
                        || source == PriceTransactionSource.DIRECT_TRADE)
                && sourceReference == null) {
            throw new IllegalArgumentException("verified price evidence requires a source reference");
        }
    }

    /** 출처를 증명할 참조가 없는 레거시 체결. 공개 시세에는 기본적으로 포함하지 않는다. */
    public PriceTransaction(String setNumber, long price, int quantity, Instant executedAt, SetCondition condition) {
        this(setNumber, price, quantity, executedAt, condition, PriceTransactionSource.LEGACY_UNVERIFIED, null);
    }

    /** 상태·출처 미지정 레거시 체결 — 미개봉으로 간주하되 공개 시세에는 포함하지 않는다. */
    public PriceTransaction(String setNumber, long price, int quantity, Instant executedAt) {
        this(setNumber, price, quantity, executedAt, SetCondition.NEW_SEALED);
    }

    private static String normalizeReference(String reference) {
        if (reference == null) {
            return null;
        }
        String normalized = reference.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
