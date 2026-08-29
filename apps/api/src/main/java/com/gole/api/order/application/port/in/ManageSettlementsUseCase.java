package com.gole.api.order.application.port.in;

import java.time.Instant;
import java.util.List;

/** 관리자용 판매자 정산 원장 조회와 수동 지급 확인. */
public interface ManageSettlementsUseCase {

    List<SettlementSummary> list(SettlementStatus status, int limit);

    long count(SettlementStatus status);

    /** 외부 이체 전에 원장을 운영자에게 원자적으로 배정한다. */
    SettlementSummary claimManualPayout(String orderId, String operatorId);

    /** 본인 배정 또는 남의 장기 정체 배정을 외부 지급 확인 전까지 차단한다. */
    SettlementSummary reconcileManualPayout(String orderId, String operatorId, String reason);

    /**
     * 차단 원장의 외부 지급 결과를 확인한 뒤 지급 완료로 기록한다. 미지급이면 MANUAL은 현재
     * 운영자 작업으로, PROVIDER는 자동 재시도 큐로 복구한다.
     */
    SettlementSummary recoverBlockedPayout(
            String orderId, String operatorId, boolean alreadyPaid, String paymentReference, String reason);

    /** 자신에게 배정된 원장만 지급 증빙과 함께 완료할 수 있다. */
    SettlementSummary markPaid(String orderId, String operatorId, String paymentReference);

    /** 수수료 총액·건수 집계. (shipping-and-fees R5.6) 상태를 null로 주면 전체. */
    FeeTotals totals(SettlementStatus status);

    enum SettlementStatus {
        PENDING,
        PAYOUT_IN_PROGRESS,
        PAYOUT_FAILED,
        PAYOUT_BLOCKED,
        PAID
    }

    record FeeTotals(long count, long grossTotal, long feeTotal, long payoutTotal) {}

    /**
     * @param payableAt 운영 지급 유예가 끝나는 시각. 이 시각 전에는 {@link #markPaid}가 거부된다.
     *     별도 계약의 보류 기간과 취소·분쟁 정책은 이 값 외에 함께 검증해야 한다.
     */
    /** {@link SettlementStatus#PAID}는 기획 용어 SETTLED와 같은, 외부 판매자 지급 확인 상태다. */
    record SettlementSummary(
            String orderId,
            String sellerId,
            long grossAmount,
            long fee,
            long payout,
            double feeRate,
            SettlementStatus status,
            String paymentReference,
            Instant createdAt,
            Instant payableAt,
            Instant paidAt,
            int payoutAttempts,
            String payoutOperatorId,
            Instant payoutAttemptedAt,
            Instant payoutNextAttemptAt,
            String payoutError) {}
}
