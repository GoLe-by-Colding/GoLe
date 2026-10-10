package com.gole.api.order.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.common.exception.ConflictException;
import com.gole.api.order.domain.model.ManualPayoutPolicy.Recovery;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ManualPayoutPolicyTest {

    private static final Instant NOW = Instant.parse("2026-10-10T00:00:00Z");
    private static final Duration HOLDBACK = Duration.ofDays(3);
    private static final Duration CLAIM_TIMEOUT = Duration.ofMinutes(30);

    @Test
    @DisplayName("새로 선점할 수 있는 상태는 대기와 지급 실패뿐이다 — 차단은 외부 확인 없이 다시 선점되지 않는다")
    void claimable_isPendingAndFailedOnly() {
        assertThat(ManualPayoutPolicy.CLAIMABLE)
                .containsExactly(SettlementStatus.PENDING, SettlementStatus.PAYOUT_FAILED);
    }

    @Test
    @DisplayName("같은 운영자가 선점 중인 원장만 다시 선점해도 그대로 돌려준다")
    void alreadyClaimedBy_onlyForOwnInProgressClaim() {
        assertThat(ManualPayoutPolicy.alreadyClaimedBy(
                        ledger(SettlementStatus.PAYOUT_IN_PROGRESS, "admin-1"), "admin-1"))
                .isTrue();
        assertThat(ManualPayoutPolicy.alreadyClaimedBy(
                        ledger(SettlementStatus.PAYOUT_IN_PROGRESS, "admin-2"), "admin-1"))
                .isFalse();
        assertThat(ManualPayoutPolicy.alreadyClaimedBy(ledger(SettlementStatus.PENDING, null), "admin-1"))
                .isFalse();
    }

    @Test
    @DisplayName("선점이 빗나가면 진행 중이면 ALREADY_CLAIMED, 그 밖은 STATE_CONFLICT 로 거부한다")
    void claimRejected_choosesCodeByCurrentState() {
        assertThat(ManualPayoutPolicy.claimRejected(ledger(SettlementStatus.PAYOUT_IN_PROGRESS, "admin-2"))
                        .getCode())
                .isEqualTo("SETTLEMENT_ALREADY_CLAIMED");
        assertThat(ManualPayoutPolicy.claimRejected(ledger(SettlementStatus.PAYOUT_BLOCKED, null))
                        .getCode())
                .isEqualTo("SETTLEMENT_STATE_CONFLICT");
    }

    @Test
    @DisplayName("지급 유예는 원장 적재 시각이 없으면 잠그고, 유예 중이면 지급 가능 시각과 함께 거부한다")
    void requireHoldbackElapsed_locksMissingCreatedAtAndActiveHoldback() {
        assertThatThrownBy(() -> ManualPayoutPolicy.requireHoldbackElapsed(
                        new SettlementLedger("order-1", SettlementStatus.PENDING, null, null, null, null),
                        HOLDBACK,
                        NOW))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "SETTLEMENT_DATA_INVALID");
        assertThatThrownBy(() -> ManualPayoutPolicy.requireHoldbackElapsed(
                        createdAt(NOW.minus(Duration.ofDays(1))), HOLDBACK, NOW))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "SETTLEMENT_HOLDBACK_ACTIVE")
                .hasMessageContaining(NOW.plus(Duration.ofDays(2)).toString());
        assertThatCode(() -> ManualPayoutPolicy.requireHoldbackElapsed(createdAt(NOW.minus(HOLDBACK)), HOLDBACK, NOW))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("구매 확정된 주문만 지급한다")
    void requirePayableOrder_acceptsOnlyCompleted() {
        assertThatCode(() -> ManualPayoutPolicy.requirePayableOrder(OrderStatus.COMPLETED))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> ManualPayoutPolicy.requirePayableOrder(OrderStatus.DISPUTED))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "SETTLEMENT_ORDER_NOT_COMPLETED");
    }

    @Test
    @DisplayName("본인 선점은 바로 차단하고, 남의 선점은 정체 시간이 지나야 차단한다")
    void reconcileBlockNote_ownClaimImmediatelyForeignOnlyWhenStale() {
        SettlementLedger own = inProgress("admin-1", NOW.minusSeconds(60));
        SettlementLedger foreignActive = inProgress("admin-2", NOW.minusSeconds(60));
        SettlementLedger foreignStale = inProgress("admin-2", NOW.minus(CLAIM_TIMEOUT));

        assertThat(ManualPayoutPolicy.reconcileBlockNote(own, "admin-1", "이체 결과 미확인", NOW, CLAIM_TIMEOUT))
                .isEqualTo("담당자 지급 결과 확인 필요: 이체 결과 미확인");
        assertThatThrownBy(
                        () -> ManualPayoutPolicy.reconcileBlockNote(foreignActive, "admin-1", "정체", NOW, CLAIM_TIMEOUT))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "SETTLEMENT_CLAIM_STILL_ACTIVE");
        assertThat(ManualPayoutPolicy.reconcileBlockNote(foreignStale, "admin-1", "정체", NOW, CLAIM_TIMEOUT))
                .isEqualTo("장기 정체 지급 확인 필요: 정체");
    }

    @Test
    @DisplayName("진행 중이 아닌 원장은 재조정하지 않는다")
    void reconcileBlockNote_rejectsNotInProgress() {
        assertThatThrownBy(() -> ManualPayoutPolicy.reconcileBlockNote(
                        ledger(SettlementStatus.PAYOUT_BLOCKED, null), "admin-1", "사유", NOW, CLAIM_TIMEOUT))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "SETTLEMENT_STATE_CONFLICT")
                .hasMessageContaining("진행 중인 정산만");
    }

    @Test
    @DisplayName("차단 원장만 복구하고, 외부 지급 확인에는 증빙이 필요하며, 같은 증빙의 지급 완료 재요청은 멱등이다")
    void recovery_pathsAndGuards() {
        SettlementLedger blocked = ledger(SettlementStatus.PAYOUT_BLOCKED, null);
        SettlementLedger paid = new SettlementLedger("order-1", SettlementStatus.PAID, null, null, "bank-42", NOW);

        assertThat(ManualPayoutPolicy.recovery(blocked, false, null)).isEqualTo(Recovery.RETRY);
        assertThat(ManualPayoutPolicy.recovery(blocked, true, "bank-42")).isEqualTo(Recovery.RECORD_PAID);
        assertThat(ManualPayoutPolicy.recovery(paid, true, " bank-42 ")).isEqualTo(Recovery.ALREADY_RECORDED);
        assertThatThrownBy(() -> ManualPayoutPolicy.recovery(blocked, true, " "))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "SETTLEMENT_REFERENCE_REQUIRED");
        assertThatThrownBy(() -> ManualPayoutPolicy.recovery(paid, true, "bank-99"))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "SETTLEMENT_STATE_CONFLICT");
        assertThatThrownBy(() -> ManualPayoutPolicy.recovery(ledger(SettlementStatus.PENDING, null), false, null))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "SETTLEMENT_STATE_CONFLICT");
    }

    @Test
    @DisplayName("지급 완료가 빗나가면 같은 증빙은 멱등, 다른 증빙·남의 선점·선점 전은 각각의 사유로 거부한다")
    void requireSamePaidEvidence_distinguishesEveryMiss() {
        SettlementLedger paid = new SettlementLedger("order-1", SettlementStatus.PAID, null, null, "bank-42", NOW);

        assertThatCode(() -> ManualPayoutPolicy.requireSamePaidEvidence(paid, "bank-42"))
                .doesNotThrowAnyException();
        assertThat(codeOf(() -> ManualPayoutPolicy.requireSamePaidEvidence(paid, "bank-99")))
                .isEqualTo("SETTLEMENT_ALREADY_PAID");
        assertThat(codeOf(() -> ManualPayoutPolicy.requireSamePaidEvidence(
                        ledger(SettlementStatus.PAYOUT_IN_PROGRESS, "admin-2"), "bank-42")))
                .isEqualTo("SETTLEMENT_CLAIM_OWNER_MISMATCH");
        assertThat(codeOf(() ->
                        ManualPayoutPolicy.requireSamePaidEvidence(ledger(SettlementStatus.PENDING, null), "bank-42")))
                .isEqualTo("SETTLEMENT_CLAIM_REQUIRED");
    }

    @Test
    @DisplayName("수동 지급은 MANUAL 모드와 지급대행 계약 확인이 모두 있어야 열린다")
    void requireManualMode_needsModeAndContract() {
        assertThat(codeOf(() -> ManualPayoutPolicy.requireManualMode(false, true)))
                .isEqualTo("SETTLEMENT_MANUAL_MODE_REQUIRED");
        assertThat(codeOf(() -> ManualPayoutPolicy.requireManualMode(true, false)))
                .isEqualTo("SETTLEMENT_CONTRACT_NOT_VERIFIED");
        assertThatCode(() -> ManualPayoutPolicy.requireManualMode(true, true)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("지급 메모는 비면 기본 문구, 500자를 넘으면 자른다")
    void payoutNote_defaultsAndTruncates() {
        assertThat(SettlementLedger.payoutNote("  ")).isEqualTo("알 수 없는 지급대행 오류");
        assertThat(SettlementLedger.payoutNote("가".repeat(501))).hasSize(500);
    }

    private static String codeOf(Runnable action) {
        try {
            action.run();
        } catch (ConflictException exception) {
            return exception.getCode();
        }
        throw new AssertionError("거부되지 않았습니다");
    }

    private static SettlementLedger ledger(SettlementStatus status, String operatorId) {
        return new SettlementLedger("order-1", status, operatorId, NOW, null, NOW.minus(HOLDBACK));
    }

    private static SettlementLedger inProgress(String operatorId, Instant attemptedAt) {
        return new SettlementLedger(
                "order-1", SettlementStatus.PAYOUT_IN_PROGRESS, operatorId, attemptedAt, null, NOW.minus(HOLDBACK));
    }

    private static SettlementLedger createdAt(Instant createdAt) {
        return new SettlementLedger("order-1", SettlementStatus.PENDING, null, null, null, createdAt);
    }
}
