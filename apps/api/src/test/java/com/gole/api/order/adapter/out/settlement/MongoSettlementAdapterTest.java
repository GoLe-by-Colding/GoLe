package com.gole.api.order.adapter.out.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.common.exception.ConflictException;
import com.gole.api.order.application.port.in.ManageSettlementsUseCase.SettlementStatus;
import com.gole.api.order.domain.model.FeePolicy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

class MongoSettlementAdapterTest {

    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final MongoSettlementAdapter adapter =
            new MongoSettlementAdapter(mongo, Clock.fixed(NOW, ZoneOffset.UTC), new FeePolicy(0.05, 0, 0));

    @Test
    void settleOnce_usesAtomicUpsertForOneLedgerPerOrder() {
        adapter.settleOnce("order-1", "seller-1", 100_000);

        verify(mongo).upsert(any(Query.class), any(Update.class), eq(SettlementDocument.class));
    }

    @Test
    void markPaid_returnsAtomicallyUpdatedLedger() {
        SettlementDocument paid = new SettlementDocument(
                "order-1", "seller-1", 100_000, 5_000, 95_000, 0.05, "PAID", "bank-42", NOW, NOW);
        when(mongo.findAndModify(
                        any(Query.class),
                        any(Update.class),
                        any(FindAndModifyOptions.class),
                        eq(SettlementDocument.class)))
                .thenReturn(paid);

        var result = adapter.markPaid("order-1", "bank-42");

        assertThat(result.status()).isEqualTo(SettlementStatus.PAID);
        assertThat(result.payout()).isEqualTo(95_000);
        assertThat(result.paymentReference()).isEqualTo("bank-42");
    }

    @Test
    void count_filtersBySettlementStatus() {
        when(mongo.count(any(Query.class), eq(SettlementDocument.class))).thenReturn(7L);

        assertThat(adapter.count(SettlementStatus.PENDING)).isEqualTo(7L);

        verify(mongo).count(any(Query.class), eq(SettlementDocument.class));
    }

    @Test
    void markPaid_rejectsReferenceAlreadyUsedByAnotherSettlement() {
        when(mongo.findAndModify(
                        any(Query.class),
                        any(Update.class),
                        any(FindAndModifyOptions.class),
                        eq(SettlementDocument.class)))
                .thenThrow(new DuplicateKeyException("duplicate paymentReference"));

        assertThatThrownBy(() -> adapter.markPaid("order-2", "bank-42"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("이미 다른 정산");
    }

    @Test
    void markPaid_doesNotOverwriteEvidenceOfCompletedSettlement() {
        SettlementDocument paid = new SettlementDocument(
                "order-1", "seller-1", 100_000, 5_000, 95_000, 0.05, "PAID", "bank-original", NOW, NOW);
        when(mongo.findAndModify(
                        any(Query.class),
                        any(Update.class),
                        any(FindAndModifyOptions.class),
                        eq(SettlementDocument.class)))
                .thenReturn(null);
        when(mongo.findById("order-1", SettlementDocument.class)).thenReturn(paid);

        assertThatThrownBy(() -> adapter.markPaid("order-1", "bank-different"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("이미 다른 지급 증빙");
    }
}
