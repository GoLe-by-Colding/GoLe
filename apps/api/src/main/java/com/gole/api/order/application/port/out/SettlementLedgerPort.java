package com.gole.api.order.application.port.out;

import com.gole.api.order.application.port.in.GetSellerSettlementsUseCase.SellerSettlementSummary;
import com.gole.api.order.application.port.in.ManageSettlementsUseCase.FeeTotals;
import com.gole.api.order.application.port.in.ManageSettlementsUseCase.SettlementSummary;
import com.gole.api.order.domain.model.SettlementStatus;
import java.util.List;

/**
 * Outbound port: 판매자 정산 원장의 조회와 관리자 수동 지급 전이. 지급 선점·대사·복구·완료는 저장소의 조건부 갱신으로 원자 처리한다.
 */
public interface SettlementLedgerPort {

    List<SettlementSummary> list(SettlementStatus status, int limit);

    List<SellerSettlementSummary> listBySeller(String sellerId, int limit);

    long count(SettlementStatus status);

    FeeTotals totals(SettlementStatus status);

    SettlementSummary claimManualPayout(String orderId, String operatorId);

    SettlementSummary reconcileManualPayout(String orderId, String operatorId, String reason);

    SettlementSummary recoverBlockedPayout(
            String orderId, String operatorId, boolean alreadyPaid, String paymentReference, String reason);

    SettlementSummary markPaid(String orderId, String operatorId, String paymentReference);
}
