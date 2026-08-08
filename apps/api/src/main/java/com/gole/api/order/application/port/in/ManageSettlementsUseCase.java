package com.gole.api.order.application.port.in;

import java.time.Instant;
import java.util.List;

/** 관리자용 판매자 정산 원장 조회와 수동 지급 확인. */
public interface ManageSettlementsUseCase {

    List<SettlementSummary> list(SettlementStatus status, int limit);

    long count(SettlementStatus status);

    SettlementSummary markPaid(String orderId, String paymentReference);

    enum SettlementStatus {
        PENDING,
        PAID
    }

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
