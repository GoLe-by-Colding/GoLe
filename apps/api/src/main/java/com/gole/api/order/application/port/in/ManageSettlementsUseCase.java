package com.gole.api.order.application.port.in;

import java.time.Instant;
import java.util.List;

/** 관리자용 판매자 정산 원장 조회와 수동 지급 확인. */
public interface ManageSettlementsUseCase {

    List<SettlementSummary> list(SettlementStatus status, int limit);

    long count(SettlementStatus status);

    SettlementSummary markPaid(String orderId, String paymentReference);

    /** 수수료 총액·건수 집계. (shipping-and-fees R5.6) 상태를 null로 주면 전체. */
    FeeTotals totals(SettlementStatus status);

    enum SettlementStatus {
        PENDING,
        PAID
    }

    record FeeTotals(long count, long grossTotal, long feeTotal, long payoutTotal) {}

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
            Instant paidAt) {}
}
