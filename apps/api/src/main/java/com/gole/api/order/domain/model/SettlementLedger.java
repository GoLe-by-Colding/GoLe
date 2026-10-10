package com.gole.api.order.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 수동 지급 규칙이 판단에 쓰는 정산 원장 상태. 금액·수수료처럼 전이와 무관한 값은 담지 않는다.
 *
 * @param payoutOperatorId 지금 지급 작업을 선점한 운영자. 자동 지급이거나 선점 전이면 {@code null}
 * @param createdAt 원장 적재 시각. 지급 유예 기간의 기준이며, 없으면 지급을 잠근다
 */
public record SettlementLedger(
        String orderId,
        SettlementStatus status,
        String payoutOperatorId,
        Instant payoutAttemptedAt,
        String paymentReference,
        Instant createdAt) {

    public SettlementLedger {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(status, "status");
    }

    private static final int MAX_PAYOUT_NOTE_LENGTH = 500;

    public boolean is(SettlementStatus candidate) {
        return status == candidate;
    }

    /** 원장 {@code payoutError}에 남길 지급 메모. 비면 기본 문구, 500자를 넘으면 자른다. */
    public static String payoutNote(String text) {
        String value = text == null || text.isBlank() ? "알 수 없는 지급대행 오류" : text.trim();
        return value.length() > MAX_PAYOUT_NOTE_LENGTH ? value.substring(0, MAX_PAYOUT_NOTE_LENGTH) : value;
    }
}
