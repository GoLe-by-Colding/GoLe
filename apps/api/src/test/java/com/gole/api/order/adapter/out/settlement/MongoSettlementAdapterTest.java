package com.gole.api.order.adapter.out.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.common.exception.ConflictException;
import com.gole.api.order.application.port.in.ManageSettlementsUseCase.SettlementStatus;
import com.gole.api.order.application.port.out.AutomaticSettlementPort.Candidate;
import com.gole.api.order.application.port.out.OrderRepositoryPort;
import com.gole.api.order.domain.model.FeePolicy;
import com.gole.api.order.domain.model.Order;
import com.gole.api.order.domain.model.OrderStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

class MongoSettlementAdapterTest {

    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");
    private static final String OPERATOR = "admin-1";
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final SettlementProperties properties = new SettlementProperties();
    private final OrderRepositoryPort orders = mock(OrderRepositoryPort.class);
    private final MongoSettlementAdapter adapter = new MongoSettlementAdapter(
            mongo, Clock.fixed(NOW, ZoneOffset.UTC), new FeePolicy(0.05, 0, 0), properties, orders);

    @BeforeEach
    void completedOrderExistsByDefault() {
        properties.setMode(SettlementProperties.Mode.MANUAL);
        properties.setPayoutContractVerified(true);
        Order completed = mock(Order.class);
        when(completed.getStatus()).thenReturn(OrderStatus.COMPLETED);
        when(orders.findById(anyString())).thenReturn(Optional.of(completed));
        Instant createdAt = NOW.minus(Duration.ofDays(4));
        when(mongo.findById(anyString(), eq(SettlementDocument.class)))
                .thenReturn(new SettlementDocument(
                        "order-default", "seller-1", 100_000, 5_000, 95_000, 0.05, "PENDING", null, createdAt, null));
    }

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

        var result = adapter.markPaid("order-1", OPERATOR, "bank-42");

        assertThat(result.status()).isEqualTo(SettlementStatus.PAID);
        assertThat(result.payout()).isEqualTo(95_000);
        assertThat(result.paymentReference()).isEqualTo("bank-42");
    }

    @Test
    void markPaid_rejectsUnverifiedPayoutContract() {
        properties.setPayoutContractVerified(false);

        assertThatThrownBy(() -> adapter.markPaid("order-1", OPERATOR, "bank-42"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("계약 확인");

        verify(mongo, never())
                .findAndModify(
                        any(Query.class),
                        any(Update.class),
                        any(FindAndModifyOptions.class),
                        eq(SettlementDocument.class));
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

        assertThatThrownBy(() -> adapter.markPaid("order-2", OPERATOR, "bank-42"))
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

        assertThatThrownBy(() -> adapter.markPaid("order-1", OPERATOR, "bank-different"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("이미 다른 지급 증빙");
    }

    @Test
    void manualClaimIsRejectedWhilePayoutHoldbackIsStillOpen() {
        // 원장이 방금 적재된 주문. 유예(기본 3일)가 남아 있으면 이체를 아예 시작하지 못한다.
        SettlementDocument pending =
                new SettlementDocument("order-1", "seller-1", 100_000, 5_000, 95_000, 0.05, "PENDING", null, NOW, null);
        when(mongo.findById("order-1", SettlementDocument.class)).thenReturn(pending);

        assertThatThrownBy(() -> adapter.claimManualPayout("order-1", OPERATOR))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("지급 유예 기간");

        // 유예 위반이면 상태 전이를 시도조차 하지 않는다.
        verify(mongo, never())
                .findAndModify(
                        any(Query.class),
                        any(Update.class),
                        any(FindAndModifyOptions.class),
                        eq(SettlementDocument.class));
    }

    @Test
    void manualClaimThenMarkPaidSucceedsOnceHoldbackHasElapsed() {
        Instant settledLongAgo = NOW.minus(Duration.ofDays(4));
        SettlementDocument pending = new SettlementDocument(
                "order-1", "seller-1", 100_000, 5_000, 95_000, 0.05, "PENDING", null, settledLongAgo, null);
        SettlementDocument paid = new SettlementDocument(
                "order-1", "seller-1", 100_000, 5_000, 95_000, 0.05, "PAID", "bank-42", settledLongAgo, NOW);
        SettlementDocument claimed = new SettlementDocument(
                "order-1",
                "seller-1",
                100_000,
                5_000,
                95_000,
                0.05,
                "PAYOUT_IN_PROGRESS",
                null,
                settledLongAgo,
                null,
                0,
                "manual-attempt",
                OPERATOR,
                NOW,
                null,
                null);
        when(mongo.findById("order-1", SettlementDocument.class)).thenReturn(pending);
        when(mongo.findAndModify(
                        any(Query.class),
                        any(Update.class),
                        any(FindAndModifyOptions.class),
                        eq(SettlementDocument.class)))
                .thenReturn(claimed, paid);

        assertThat(adapter.claimManualPayout("order-1", OPERATOR).status())
                .isEqualTo(SettlementStatus.PAYOUT_IN_PROGRESS);
        assertThat(adapter.markPaid("order-1", OPERATOR, "bank-42").status()).isEqualTo(SettlementStatus.PAID);
    }

    @Test
    void manualClaimAtomicallyAssignsTheLedgerToTheCurrentOperator() {
        Instant createdAt = NOW.minus(Duration.ofDays(4));
        SettlementDocument pending = new SettlementDocument(
                "order-1", "seller-1", 100_000, 5_000, 95_000, 0.05, "PENDING", null, createdAt, null);
        SettlementDocument claimed = new SettlementDocument(
                "order-1",
                "seller-1",
                100_000,
                5_000,
                95_000,
                0.05,
                "PAYOUT_IN_PROGRESS",
                null,
                createdAt,
                null,
                0,
                "manual-attempt",
                OPERATOR,
                NOW,
                null,
                null);
        when(mongo.findById("order-1", SettlementDocument.class)).thenReturn(pending);
        when(mongo.findAndModify(
                        any(Query.class),
                        any(Update.class),
                        any(FindAndModifyOptions.class),
                        eq(SettlementDocument.class)))
                .thenReturn(claimed);

        var row = adapter.claimManualPayout("order-1", OPERATOR);

        assertThat(row.status()).isEqualTo(SettlementStatus.PAYOUT_IN_PROGRESS);
        assertThat(row.payoutOperatorId()).isEqualTo(OPERATOR);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongo)
                .findAndModify(
                        any(Query.class),
                        update.capture(),
                        any(FindAndModifyOptions.class),
                        eq(SettlementDocument.class));
        assertThat(update.getValue().getUpdateObject().get("$set", org.bson.Document.class))
                .containsEntry("status", SettlementStatus.PAYOUT_IN_PROGRESS.name())
                .containsEntry("payoutOperatorId", OPERATOR);
    }

    @Test
    void manualClaimIsIdempotentForItsOwnerAndRejectsAnotherOperator() {
        Instant createdAt = NOW.minus(Duration.ofDays(4));
        SettlementDocument claimed = new SettlementDocument(
                "order-1",
                "seller-1",
                100_000,
                5_000,
                95_000,
                0.05,
                "PAYOUT_IN_PROGRESS",
                null,
                createdAt,
                null,
                0,
                "manual-attempt",
                OPERATOR,
                NOW,
                null,
                null);
        when(mongo.findById("order-1", SettlementDocument.class)).thenReturn(claimed);

        assertThat(adapter.claimManualPayout("order-1", OPERATOR).payoutOperatorId())
                .isEqualTo(OPERATOR);
        verify(mongo, never())
                .findAndModify(
                        any(Query.class),
                        any(Update.class),
                        any(FindAndModifyOptions.class),
                        eq(SettlementDocument.class));

        when(mongo.findAndModify(
                        any(Query.class),
                        any(Update.class),
                        any(FindAndModifyOptions.class),
                        eq(SettlementDocument.class)))
                .thenReturn(null);
        assertThatThrownBy(() -> adapter.claimManualPayout("order-1", "admin-2"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("다른 운영자");
    }

    @Test
    void markPaidRequiresTheManualClaimOwner() {
        Instant createdAt = NOW.minus(Duration.ofDays(4));
        SettlementDocument claimedByOther = new SettlementDocument(
                "order-1",
                "seller-1",
                100_000,
                5_000,
                95_000,
                0.05,
                "PAYOUT_IN_PROGRESS",
                null,
                createdAt,
                null,
                0,
                "manual-attempt",
                "admin-2",
                NOW,
                null,
                null);
        when(mongo.findAndModify(
                        any(Query.class),
                        any(Update.class),
                        any(FindAndModifyOptions.class),
                        eq(SettlementDocument.class)))
                .thenReturn(null);
        when(mongo.findById("order-1", SettlementDocument.class)).thenReturn(claimedByOther);

        assertThatThrownBy(() -> adapter.markPaid("order-1", OPERATOR, "bank-42"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("배정받은 운영자");
    }

    @Test
    void claimDoesNotMakeABlockedPayoutPayableAgainWithoutExternalVerification() {
        Instant createdAt = NOW.minus(Duration.ofDays(4));
        SettlementDocument blocked = new SettlementDocument(
                "order-1",
                "seller-1",
                100_000,
                5_000,
                95_000,
                0.05,
                "PAYOUT_BLOCKED",
                null,
                createdAt,
                null,
                1,
                "provider-attempt",
                null,
                NOW.minus(Duration.ofMinutes(11)),
                null,
                "지급 결과 확인 필요");
        when(mongo.findById("order-1", SettlementDocument.class)).thenReturn(blocked);
        when(mongo.findAndModify(
                        any(Query.class),
                        any(Update.class),
                        any(FindAndModifyOptions.class),
                        eq(SettlementDocument.class)))
                .thenReturn(null);

        assertThatThrownBy(() -> adapter.claimManualPayout("order-1", OPERATOR))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("상태가 변경");

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongo)
                .findAndModify(
                        query.capture(),
                        any(Update.class),
                        any(FindAndModifyOptions.class),
                        eq(SettlementDocument.class));
        assertThat(query.getValue().getQueryObject().toString())
                .contains("PENDING", "PAYOUT_FAILED")
                .doesNotContain("PAYOUT_BLOCKED");
    }

    @Test
    void ownerReconciliationAlsoBlocksTheLedgerUntilExternalPaymentIsVerified() {
        Instant createdAt = NOW.minus(Duration.ofDays(4));
        SettlementDocument claimed = new SettlementDocument(
                "order-1",
                "seller-1",
                100_000,
                5_000,
                95_000,
                0.05,
                "PAYOUT_IN_PROGRESS",
                null,
                createdAt,
                null,
                0,
                "manual-attempt",
                OPERATOR,
                NOW,
                null,
                null);
        SettlementDocument blocked = new SettlementDocument(
                "order-1",
                "seller-1",
                100_000,
                5_000,
                95_000,
                0.05,
                "PAYOUT_BLOCKED",
                null,
                createdAt,
                null,
                0,
                "manual-attempt",
                null,
                NOW,
                null,
                "담당자 지급 결과 확인 필요: 담당자 교대");
        when(mongo.findById("order-1", SettlementDocument.class)).thenReturn(claimed);
        when(mongo.findAndModify(
                        any(Query.class),
                        any(Update.class),
                        any(FindAndModifyOptions.class),
                        eq(SettlementDocument.class)))
                .thenReturn(blocked);

        var row = adapter.reconcileManualPayout("order-1", OPERATOR, "담당자 교대");

        assertThat(row.status()).isEqualTo(SettlementStatus.PAYOUT_BLOCKED);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongo)
                .findAndModify(
                        any(Query.class),
                        update.capture(),
                        any(FindAndModifyOptions.class),
                        eq(SettlementDocument.class));
        assertThat(update.getValue().getUpdateObject().get("$set", org.bson.Document.class))
                .containsEntry("status", SettlementStatus.PAYOUT_BLOCKED.name())
                .containsEntry("payoutError", "담당자 지급 결과 확인 필요: 담당자 교대");
        assertThat(update.getValue().getUpdateObject().get("$unset", org.bson.Document.class))
                .containsKey("payoutOperatorId");
    }

    @Test
    void anotherOperatorCannotInterruptAnActivePayoutClaim() {
        SettlementDocument claimed = new SettlementDocument(
                "order-1",
                "seller-1",
                100_000,
                5_000,
                95_000,
                0.05,
                "PAYOUT_IN_PROGRESS",
                null,
                NOW.minus(Duration.ofDays(4)),
                null,
                0,
                "manual-attempt",
                "admin-2",
                NOW,
                null,
                null);
        when(mongo.findById("order-1", SettlementDocument.class)).thenReturn(claimed);

        assertThatThrownBy(() -> adapter.reconcileManualPayout("order-1", OPERATOR, "담당자 응답 없음"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("아직 진행 중");

        verify(mongo, never())
                .findAndModify(
                        any(Query.class),
                        any(Update.class),
                        any(FindAndModifyOptions.class),
                        eq(SettlementDocument.class));
    }

    @Test
    void staleForeignClaimMovesToBlockedUntilItsExternalResultIsVerified() {
        Instant createdAt = NOW.minus(Duration.ofDays(4));
        SettlementDocument stale = new SettlementDocument(
                "order-1",
                "seller-1",
                100_000,
                5_000,
                95_000,
                0.05,
                "PAYOUT_IN_PROGRESS",
                null,
                createdAt,
                null,
                1,
                "provider-attempt",
                null,
                NOW.minus(Duration.ofMinutes(11)),
                null,
                null);
        SettlementDocument blocked = new SettlementDocument(
                "order-1",
                "seller-1",
                100_000,
                5_000,
                95_000,
                0.05,
                "PAYOUT_BLOCKED",
                null,
                createdAt,
                null,
                1,
                "provider-attempt",
                null,
                NOW.minus(Duration.ofMinutes(11)),
                null,
                "장기 정체 지급 확인 필요: 지급사 조회 필요");
        when(mongo.findById("order-1", SettlementDocument.class)).thenReturn(stale);
        when(mongo.findAndModify(
                        any(Query.class),
                        any(Update.class),
                        any(FindAndModifyOptions.class),
                        eq(SettlementDocument.class)))
                .thenReturn(blocked);

        var row = adapter.reconcileManualPayout("order-1", OPERATOR, "지급사 조회 필요");

        assertThat(row.status()).isEqualTo(SettlementStatus.PAYOUT_BLOCKED);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongo)
                .findAndModify(
                        any(Query.class),
                        update.capture(),
                        any(FindAndModifyOptions.class),
                        eq(SettlementDocument.class));
        assertThat(update.getValue().getUpdateObject().get("$set", org.bson.Document.class))
                .containsEntry("status", SettlementStatus.PAYOUT_BLOCKED.name())
                .containsEntry("payoutError", "장기 정체 지급 확인 필요: 지급사 조회 필요");
        assertThat(update.getValue().getUpdateObject().get("$unset", org.bson.Document.class))
                .containsKey("payoutOperatorId");
    }

    @Test
    void blockedPayoutCanBeRecordedPaidOnlyWithExternalEvidence() {
        Instant createdAt = NOW.minus(Duration.ofDays(4));
        SettlementDocument blocked = new SettlementDocument(
                "order-1",
                "seller-1",
                100_000,
                5_000,
                95_000,
                0.05,
                "PAYOUT_BLOCKED",
                null,
                createdAt,
                null,
                1,
                "provider-attempt",
                null,
                NOW.minus(Duration.ofMinutes(11)),
                null,
                "외부 결과 확인 필요");
        SettlementDocument paid = new SettlementDocument(
                "order-1", "seller-1", 100_000, 5_000, 95_000, 0.05, "PAID", "provider-ref", createdAt, NOW);
        when(mongo.findById("order-1", SettlementDocument.class)).thenReturn(blocked);
        when(mongo.findAndModify(
                        any(Query.class),
                        any(Update.class),
                        any(FindAndModifyOptions.class),
                        eq(SettlementDocument.class)))
                .thenReturn(paid);

        var row = adapter.recoverBlockedPayout("order-1", OPERATOR, true, "provider-ref", "지급사 거래 조회에서 성공 확인");

        assertThat(row.status()).isEqualTo(SettlementStatus.PAID);
        assertThat(row.paymentReference()).isEqualTo("provider-ref");
    }

    @Test
    void blockedPayoutCanBeReassignedOnlyAfterExternalNonPaymentIsConfirmed() {
        Instant createdAt = NOW.minus(Duration.ofDays(4));
        SettlementDocument blocked = new SettlementDocument(
                "order-1",
                "seller-1",
                100_000,
                5_000,
                95_000,
                0.05,
                "PAYOUT_BLOCKED",
                null,
                createdAt,
                null,
                1,
                "provider-attempt",
                null,
                NOW.minus(Duration.ofMinutes(11)),
                null,
                "외부 결과 확인 필요");
        SettlementDocument reclaimed = new SettlementDocument(
                "order-1",
                "seller-1",
                100_000,
                5_000,
                95_000,
                0.05,
                "PAYOUT_IN_PROGRESS",
                null,
                createdAt,
                null,
                1,
                "manual-recovery",
                OPERATOR,
                NOW,
                null,
                "외부 미지급 확인 후 수동 복구: 지급사 거래 없음 확인");
        when(mongo.findById("order-1", SettlementDocument.class)).thenReturn(blocked);
        when(mongo.findAndModify(
                        any(Query.class),
                        any(Update.class),
                        any(FindAndModifyOptions.class),
                        eq(SettlementDocument.class)))
                .thenReturn(reclaimed);

        var row = adapter.recoverBlockedPayout("order-1", OPERATOR, false, null, "지급사 거래 없음 확인");

        assertThat(row.status()).isEqualTo(SettlementStatus.PAYOUT_IN_PROGRESS);
        assertThat(row.payoutOperatorId()).isEqualTo(OPERATOR);
    }

    @Test
    void blockedProviderPayoutCanBeRequeuedOnlyAfterExternalNonPaymentIsConfirmed() {
        properties.setMode(SettlementProperties.Mode.PROVIDER);
        Instant createdAt = NOW.minus(Duration.ofDays(4));
        SettlementDocument blocked = new SettlementDocument(
                "order-1",
                "seller-1",
                100_000,
                5_000,
                95_000,
                0.05,
                "PAYOUT_BLOCKED",
                null,
                createdAt,
                null,
                5,
                "provider-attempt",
                null,
                NOW.minus(Duration.ofMinutes(11)),
                null,
                "외부 결과 확인 필요");
        SettlementDocument retryable = new SettlementDocument(
                "order-1",
                "seller-1",
                100_000,
                5_000,
                95_000,
                0.05,
                "PAYOUT_FAILED",
                null,
                createdAt,
                null,
                0,
                null,
                null,
                NOW,
                NOW,
                "외부 미지급 확인 후 자동 재시도 요청 (admin-1): 지급사 거래 없음 확인");
        when(mongo.findById("order-1", SettlementDocument.class)).thenReturn(blocked);
        when(mongo.findAndModify(
                        any(Query.class),
                        any(Update.class),
                        any(FindAndModifyOptions.class),
                        eq(SettlementDocument.class)))
                .thenReturn(retryable);

        var row = adapter.recoverBlockedPayout("order-1", OPERATOR, false, null, "지급사 거래 없음 확인");

        assertThat(row.status()).isEqualTo(SettlementStatus.PAYOUT_FAILED);
        assertThat(row.payoutAttempts()).isZero();
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongo)
                .findAndModify(
                        any(Query.class),
                        update.capture(),
                        any(FindAndModifyOptions.class),
                        eq(SettlementDocument.class));
        assertThat(update.getValue().getUpdateObject().get("$set", org.bson.Document.class))
                .containsEntry("status", SettlementStatus.PAYOUT_FAILED.name())
                .containsEntry("payoutAttempts", 0)
                .containsEntry("payoutNextAttemptAt", NOW);
        assertThat(update.getValue().getUpdateObject().get("$unset", org.bson.Document.class))
                .containsKeys("payoutAttemptId", "payoutOperatorId");
    }

    @Test
    void blockedProviderPayoutCannotBeRequeuedWithoutVerifiedContract() {
        properties.setMode(SettlementProperties.Mode.PROVIDER);
        properties.setPayoutContractVerified(false);
        Instant createdAt = NOW.minus(Duration.ofDays(4));
        SettlementDocument blocked = new SettlementDocument(
                "order-1",
                "seller-1",
                100_000,
                5_000,
                95_000,
                0.05,
                "PAYOUT_BLOCKED",
                null,
                createdAt,
                null,
                5,
                "provider-attempt",
                null,
                NOW.minus(Duration.ofMinutes(11)),
                null,
                "외부 결과 확인 필요");
        when(mongo.findById("order-1", SettlementDocument.class)).thenReturn(blocked);

        assertThatThrownBy(() -> adapter.recoverBlockedPayout("order-1", OPERATOR, false, null, "지급사 거래 없음 확인"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("계약 확인");

        verify(mongo, never())
                .findAndModify(
                        any(Query.class),
                        any(Update.class),
                        any(FindAndModifyOptions.class),
                        eq(SettlementDocument.class));
    }

    @Test
    void summaryExposesWhenThePayoutBecomesAllowed() {
        SettlementDocument pending =
                new SettlementDocument("order-1", "seller-1", 100_000, 5_000, 95_000, 0.05, "PENDING", null, NOW, null);
        when(mongo.find(any(Query.class), eq(SettlementDocument.class))).thenReturn(List.of(pending));

        var row = adapter.list(SettlementStatus.PENDING, 10).getFirst();

        assertThat(row.payableAt()).isEqualTo(NOW.plus(properties.getPayoutHoldback()));
    }

    /** 환불·분쟁으로 빠진 주문은 원장에 PENDING이 남아 있어도 지급되면 안 된다. */
    @Test
    void manualClaimIsBlockedWhenTheOrderIsNoLongerCompleted() {
        Instant settledLongAgo = NOW.minus(Duration.ofDays(4));
        SettlementDocument pending = new SettlementDocument(
                "order-1", "seller-1", 100_000, 5_000, 95_000, 0.05, "PENDING", null, settledLongAgo, null);
        when(mongo.findById("order-1", SettlementDocument.class)).thenReturn(pending);
        Order refunded = mock(Order.class);
        when(refunded.getStatus()).thenReturn(OrderStatus.REFUNDED);
        when(orders.findById("order-1")).thenReturn(Optional.of(refunded));

        assertThatThrownBy(() -> adapter.claimManualPayout("order-1", OPERATOR))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("구매 확정된 주문만");

        verify(mongo, never())
                .findAndModify(
                        any(Query.class),
                        any(Update.class),
                        any(FindAndModifyOptions.class),
                        eq(SettlementDocument.class));
    }

    @Test
    void manualClaimIsBlockedWhileTheOrderIsDisputed() {
        Instant settledLongAgo = NOW.minus(Duration.ofDays(4));
        SettlementDocument pending = new SettlementDocument(
                "order-1", "seller-1", 100_000, 5_000, 95_000, 0.05, "PENDING", null, settledLongAgo, null);
        when(mongo.findById("order-1", SettlementDocument.class)).thenReturn(pending);
        Order disputed = mock(Order.class);
        when(disputed.getStatus()).thenReturn(OrderStatus.DISPUTED);
        when(orders.findById("order-1")).thenReturn(Optional.of(disputed));

        assertThatThrownBy(() -> adapter.claimManualPayout("order-1", OPERATOR))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("구매 확정된 주문만");
    }

    @Test
    void manualClaimFailsClosedWhenOrderDoesNotExist() {
        when(orders.findById("missing-order")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.claimManualPayout("missing-order", OPERATOR))
                .isInstanceOf(com.gole.api.common.exception.NotFoundException.class)
                .hasMessageContaining("주문을 찾을 수 없습니다");

        verify(mongo, never())
                .findAndModify(
                        any(Query.class),
                        any(Update.class),
                        any(FindAndModifyOptions.class),
                        eq(SettlementDocument.class));
    }

    @Test
    void manualClaimLocksLedgerWhenCreatedAtIsMissing() {
        SettlementDocument damaged = new SettlementDocument(
                "order-1", "seller-1", 100_000, 5_000, 95_000, 0.05, "PENDING", null, null, null);
        when(mongo.findById("order-1", SettlementDocument.class)).thenReturn(damaged);

        assertThatThrownBy(() -> adapter.claimManualPayout("order-1", OPERATOR))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("생성 시각");

        verify(mongo, never())
                .findAndModify(
                        any(Query.class),
                        any(Update.class),
                        any(FindAndModifyOptions.class),
                        eq(SettlementDocument.class));
    }

    /** 같은 주문으로 여러 번 불려도 원장은 한 행이어야 한다(exactly-once). */
    @Test
    void settleOnce_isIdempotentAcrossRepeatedCalls() {
        adapter.settleOnce("order-1", "seller-1", 100_000);
        adapter.settleOnce("order-1", "seller-1", 100_000);
        adapter.settleOnce("order-1", "seller-1", 100_000);

        // setOnInsert 기반 upsert 3회 — 두 번째부터는 기존 문서를 덮지 않는다.
        verify(mongo, times(3)).upsert(any(Query.class), any(Update.class), eq(SettlementDocument.class));
    }

    /** 판매자 조회는 sellerId 로 좁혀져야 한다 — 남의 정산이 섞이면 안 된다. */
    @Test
    void listBySeller_scopesTheLedgerToThatSeller() {
        SettlementDocument mine =
                new SettlementDocument("order-1", "seller-1", 100_000, 5_000, 95_000, 0.05, "PENDING", null, NOW, null);
        when(mongo.find(any(Query.class), eq(SettlementDocument.class))).thenReturn(List.of(mine));

        var rows = adapter.listBySeller("seller-1", 50);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(query.capture(), eq(SettlementDocument.class));
        assertThat(query.getValue().getQueryObject().get("sellerId")).isEqualTo("seller-1");
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().orderId()).isEqualTo("order-1");
    }

    @Test
    void automaticClaimUsesHoldbackCutoffAndAtomicallyRecordsAttempt() {
        Instant createdAt = NOW.minus(Duration.ofDays(4));
        SettlementDocument claimed = new SettlementDocument(
                "order-1",
                "seller-1",
                100_000,
                5_000,
                95_000,
                0.05,
                SettlementStatus.PAYOUT_IN_PROGRESS.name(),
                null,
                createdAt,
                null,
                1,
                "attempt-1",
                null,
                NOW,
                null,
                null);
        when(mongo.findAndModify(
                        any(Query.class),
                        any(Update.class),
                        any(FindAndModifyOptions.class),
                        eq(SettlementDocument.class)))
                .thenReturn(claimed);

        Optional<Candidate> result = adapter.claimNext(NOW, Duration.ofDays(3), Duration.ofMinutes(10), "attempt-1");

        assertThat(result).contains(new Candidate("order-1", "seller-1", 95_000, "attempt-1", 1));
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongo)
                .findAndModify(
                        query.capture(),
                        update.capture(),
                        any(FindAndModifyOptions.class),
                        eq(SettlementDocument.class));
        org.bson.Document criteria = query.getValue().getQueryObject();
        java.util.List<?> conjunction = criteria.getList("$and", Object.class);
        org.bson.Document createdAtCriterion =
                (org.bson.Document) ((org.bson.Document) conjunction.getFirst()).get("createdAt");
        assertThat(createdAtCriterion).containsEntry("$ne", null).containsEntry("$lte", NOW.minus(Duration.ofDays(3)));
        assertThat(criteria.toString())
                .contains("createdAt")
                .contains("PENDING")
                .contains("PAYOUT_FAILED")
                .contains("PAYOUT_IN_PROGRESS")
                .contains("payoutOperatorId")
                .contains("$exists")
                .contains("payoutAttempts");
        assertThat(query.getValue().getSortObject().get("createdAt")).isEqualTo(1);
        assertThat(update.getValue().getUpdateObject().get("$set", org.bson.Document.class))
                .containsEntry("status", SettlementStatus.PAYOUT_IN_PROGRESS.name())
                .containsEntry("payoutAttemptId", "attempt-1")
                .containsEntry("payoutAttemptedAt", NOW);
        assertThat(update.getValue().getUpdateObject().get("$inc", org.bson.Document.class))
                .containsEntry("payoutAttempts", 1);
    }

    @Test
    void exhaustedOrStaleFinalProviderClaimsAreBlockedBeforeAnotherExternalCall() {
        adapter.blockExhaustedClaims(NOW, Duration.ofMinutes(10), 5);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateMulti(query.capture(), update.capture(), eq(SettlementDocument.class));
        assertThat(query.getValue().getQueryObject().toString())
                .contains(
                        "PAYOUT_FAILED",
                        "PAYOUT_IN_PROGRESS",
                        "payoutAttempts",
                        "payoutOperatorId",
                        "$exists",
                        "$gte",
                        "5")
                .contains(NOW.minus(Duration.ofMinutes(10)).toString());
        assertThat(update.getValue().getUpdateObject().get("$set", org.bson.Document.class))
                .containsEntry("status", SettlementStatus.PAYOUT_BLOCKED.name());
    }

    @Test
    void automaticClaimReturnsEmptyWhenNothingPassedTheHoldback() {
        when(mongo.findAndModify(
                        any(Query.class),
                        any(Update.class),
                        any(FindAndModifyOptions.class),
                        eq(SettlementDocument.class)))
                .thenReturn(null);

        assertThat(adapter.claimNext(NOW, Duration.ofDays(3), Duration.ofMinutes(10), "attempt-1"))
                .isEmpty();
    }

    @Test
    void providerPaidResultIsIdempotentForTheSameEvidence() {
        SettlementDocument paid = new SettlementDocument(
                "order-1",
                "seller-1",
                100_000,
                5_000,
                95_000,
                0.05,
                SettlementStatus.PAID.name(),
                "provider-ref-1",
                NOW.minus(Duration.ofDays(4)),
                NOW);
        when(mongo.findAndModify(
                        any(Query.class),
                        any(Update.class),
                        any(FindAndModifyOptions.class),
                        eq(SettlementDocument.class)))
                .thenReturn(null);
        when(mongo.findById("order-1", SettlementDocument.class)).thenReturn(paid);

        adapter.markPaid("order-1", "stale-attempt", "provider-ref-1", NOW);

        verify(mongo)
                .findAndModify(
                        any(Query.class),
                        any(Update.class),
                        any(FindAndModifyOptions.class),
                        eq(SettlementDocument.class));
    }

    @Test
    void providerPaidResultRejectsBlankEvidenceBeforeMutatingTheLedger() {
        assertThatThrownBy(() -> adapter.markPaid("order-1", "attempt-1", "  ", NOW))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("증빙 번호");

        verify(mongo, never())
                .findAndModify(
                        any(Query.class),
                        any(Update.class),
                        any(FindAndModifyOptions.class),
                        eq(SettlementDocument.class));
    }

    @Test
    void providerFailurePersistsRetryableStateAndNextAttemptTime() {
        when(mongo.findAndModify(any(Query.class), any(Update.class), eq(SettlementDocument.class)))
                .thenReturn(mock(SettlementDocument.class));

        adapter.markFailed("order-1", "attempt-1", " provider timeout ", NOW, Duration.ofMinutes(5));

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).findAndModify(query.capture(), update.capture(), eq(SettlementDocument.class));
        assertThat(query.getValue().getQueryObject().toString())
                .contains("order-1")
                .contains(SettlementStatus.PAYOUT_IN_PROGRESS.name())
                .contains("attempt-1");
        assertThat(update.getValue().getUpdateObject().get("$set", org.bson.Document.class))
                .containsEntry("status", SettlementStatus.PAYOUT_FAILED.name())
                .containsEntry("payoutError", "provider timeout")
                .containsEntry("payoutAttemptedAt", NOW)
                .containsEntry("payoutNextAttemptAt", NOW.plus(Duration.ofMinutes(5)));
    }

    @Test
    void damagedPayoutPersistsBlockedStateWithoutRetrySchedule() {
        when(mongo.findAndModify(any(Query.class), any(Update.class), eq(SettlementDocument.class)))
                .thenReturn(mock(SettlementDocument.class));

        adapter.markBlocked("order-1", "attempt-1", "order missing", NOW);

        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).findAndModify(any(Query.class), update.capture(), eq(SettlementDocument.class));
        assertThat(update.getValue().getUpdateObject().get("$set", org.bson.Document.class))
                .containsEntry("status", SettlementStatus.PAYOUT_BLOCKED.name())
                .containsEntry("payoutError", "order missing")
                .containsEntry("payoutAttemptedAt", NOW);
        assertThat(update.getValue().getUpdateObject().get("$unset", org.bson.Document.class))
                .containsKey("payoutNextAttemptAt");
    }
}
