package com.gole.api.collection.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 사용자 하루치 컬렉션 가치 기록. (collection-value-history H1)
 *
 * <p>체결 데이터가 계속 바뀌므로 지난 값을 다시 계산해 복원할 수 없다. 그날 찍은 값을 그대로 남긴다.
 * 사용자·날짜(Asia/Seoul)당 하나이고, 같은 날 다시 찍으면 덮어쓴다.
 */
public record CollectionValueSnapshot(
        String userId, LocalDate date, long ownedValue, int ownedCount, int pricedCount, Instant capturedAt) {

    public CollectionValueSnapshot {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(capturedAt, "capturedAt");
        // 값 범위 검증은 CollectionValuation과 같은 규칙을 따른다.
        new CollectionValuation(ownedValue, ownedCount, pricedCount);
    }

    public static CollectionValueSnapshot capture(
            String userId, LocalDate date, CollectionValuation valuation, Instant capturedAt) {
        return new CollectionValueSnapshot(
                userId, date, valuation.ownedValue(), valuation.ownedCount(), valuation.pricedCount(), capturedAt);
    }
}
