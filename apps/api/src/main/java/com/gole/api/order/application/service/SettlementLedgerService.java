package com.gole.api.order.application.service;

import com.gole.api.order.application.port.in.GetSellerSettlementsUseCase;
import com.gole.api.order.application.port.in.ManageSettlementsUseCase;
import com.gole.api.order.application.port.out.SettlementLedgerPort;
import java.util.List;
import org.springframework.stereotype.Service;

/** 관리자 정산 관리와 판매자 정산 조회. 원자 전이는 원장 저장소({@link SettlementLedgerPort})가 맡는다. */
@Service
public class SettlementLedgerService implements ManageSettlementsUseCase, GetSellerSettlementsUseCase {

    private final SettlementLedgerPort ledger;

    public SettlementLedgerService(SettlementLedgerPort ledger) {
        this.ledger = ledger;
    }

    @Override
    public List<SettlementSummary> list(SettlementStatus status, int limit) {
        return ledger.list(status, limit);
    }

    @Override
    public List<SellerSettlementSummary> listBySeller(String sellerId, int limit) {
        return ledger.listBySeller(sellerId, limit);
    }

    @Override
    public long count(SettlementStatus status) {
        return ledger.count(status);
    }

    @Override
    public FeeTotals totals(SettlementStatus status) {
        return ledger.totals(status);
    }

    @Override
    public SettlementSummary claimManualPayout(String orderId, String operatorId) {
        return ledger.claimManualPayout(orderId, operatorId);
    }

    @Override
    public SettlementSummary reconcileManualPayout(String orderId, String operatorId, String reason) {
        return ledger.reconcileManualPayout(orderId, operatorId, reason);
    }

    @Override
    public SettlementSummary recoverBlockedPayout(
            String orderId, String operatorId, boolean alreadyPaid, String paymentReference, String reason) {
        return ledger.recoverBlockedPayout(orderId, operatorId, alreadyPaid, paymentReference, reason);
    }

    @Override
    public SettlementSummary markPaid(String orderId, String operatorId, String paymentReference) {
        return ledger.markPaid(orderId, operatorId, paymentReference);
    }
}
