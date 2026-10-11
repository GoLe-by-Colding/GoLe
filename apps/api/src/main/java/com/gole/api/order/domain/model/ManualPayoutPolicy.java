package com.gole.api.order.domain.model;

import com.gole.api.common.exception.ConflictException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 관리자 수동 지급의 상태 전이 규칙.
 *
 * <p>선점(PENDING·PAYOUT_FAILED → PAYOUT_IN_PROGRESS), 대사(PAYOUT_IN_PROGRESS → PAYOUT_BLOCKED), 복구
 * (PAYOUT_BLOCKED → PAID·PAYOUT_FAILED·PAYOUT_IN_PROGRESS), 지급 완료(PAYOUT_IN_PROGRESS → PAID)에서 "언제 허용하고
 * 왜 거부하는가"를 여기서 정한다. 저장소 어댑터는 이 규칙이 정한 출발 상태로 조건부 갱신(CAS)만 하고, 갱신이
 * 빗나가면 다시 읽은 원장으로 이 규칙에 거부 사유를 묻는다.
 */
public final class ManualPayoutPolicy {

    /** 운영자가 새로 선점할 수 있는 상태. 차단(PAYOUT_BLOCKED)은 외부 지급 확인 없이는 다시 선점되지 않는다. */
    public static final Set<SettlementStatus> CLAIMABLE =
            Collections.unmodifiableSet(EnumSet.of(SettlementStatus.PENDING, SettlementStatus.PAYOUT_FAILED));

    private ManualPayoutPolicy() {}

    /** 수동 지급은 MANUAL 정산 모드이고 지급대행 계약을 확인한 뒤에만 연다. */
    public static void requireManualMode(boolean manualMode, boolean payoutContractVerified) {
        if (!manualMode) {
            throw new ConflictException("SETTLEMENT_MANUAL_MODE_REQUIRED", "수동 지급은 MANUAL 정산 모드에서만 사용할 수 있습니다");
        }
        requireVerifiedContract(payoutContractVerified);
    }

    public static void requireVerifiedContract(boolean payoutContractVerified) {
        if (!payoutContractVerified) {
            throw new ConflictException("SETTLEMENT_CONTRACT_NOT_VERIFIED", "지급대행 계약 확인 전에는 판매자 지급을 처리할 수 없습니다");
        }
    }

    public static String requireOperator(String operatorId) {
        if (operatorId == null || operatorId.isBlank()) {
            throw new ConflictException("SETTLEMENT_OPERATOR_REQUIRED", "정산 작업자를 확인할 수 없습니다");
        }
        return operatorId.trim();
    }

    public static String requireReason(String reason) {
        String detail = reason == null ? "" : reason.trim();
        if (detail.isBlank()) {
            throw new ConflictException("SETTLEMENT_RECONCILE_REASON_REQUIRED", "외부 지급 확인 근거와 조치 사유를 입력해야 합니다");
        }
        return detail;
    }

    /** 지급 완료에 붙일 증빙 번호. 앞뒤 공백을 지운 값을 돌려준다. */
    public static String requirePaymentReference(String paymentReference) {
        if (paymentReference == null || paymentReference.isBlank()) {
            throw new ConflictException("SETTLEMENT_REFERENCE_REQUIRED", "지급 증빙 번호를 입력해야 합니다");
        }
        return paymentReference.trim();
    }

    /** 구매 확정된 주문만 지급한다. 분쟁·취소로 바뀐 주문은 원장이 있어도 막는다. */
    public static void requirePayableOrder(OrderStatus orderStatus) {
        if (orderStatus != OrderStatus.COMPLETED) {
            throw new ConflictException(
                    "SETTLEMENT_ORDER_NOT_COMPLETED", "구매 확정된 주문만 지급할 수 있습니다 (현재 상태 %s)".formatted(orderStatus));
        }
    }

    /** 지급 유예가 끝나는 시각. 원장 적재 시각 + holdback. 적재 시각이 없으면 {@code null}. */
    public static Instant payableAt(Instant createdAt, Duration holdback) {
        return createdAt == null ? null : createdAt.plus(holdback);
    }

    public static void requireHoldbackElapsed(SettlementLedger ledger, Duration holdback, Instant now) {
        if (ledger.createdAt() == null) {
            throw new ConflictException("SETTLEMENT_DATA_INVALID", "정산 원장의 생성 시각이 없어 지급을 잠갔습니다. 운영자 확인이 필요합니다");
        }
        Instant payable = payableAt(ledger.createdAt(), holdback);
        if (now.isBefore(payable)) {
            throw new ConflictException(
                    "SETTLEMENT_HOLDBACK_ACTIVE", "지급 유예 기간이 끝나지 않아 아직 지급할 수 없습니다 (지급 가능 시각 %s)".formatted(payable));
        }
    }

    /** 같은 운영자가 이미 선점한 원장을 다시 선점하면 그대로 돌려준다(멱등). */
    public static boolean alreadyClaimedBy(SettlementLedger ledger, String operator) {
        return ledger.is(SettlementStatus.PAYOUT_IN_PROGRESS) && operator.equals(ledger.payoutOperatorId());
    }

    /** 선점 갱신이 빗나갔을 때, 다시 읽은 원장으로 거부 사유를 고른다. */
    public static ConflictException claimRejected(SettlementLedger current) {
        if (current.is(SettlementStatus.PAYOUT_IN_PROGRESS)) {
            return new ConflictException("SETTLEMENT_ALREADY_CLAIMED", "다른 운영자가 처리 중이거나 자동 지급 결과를 확인 중입니다");
        }
        return new ConflictException("SETTLEMENT_STATE_CONFLICT", "정산 상태가 변경되어 다시 확인해야 합니다");
    }

    /**
     * 진행 중인 지급을 외부 결과 확인 전까지 차단할 때 남길 메모. 본인 선점은 언제든, 남의 선점은 선점 후
     * {@code claimTimeout}이 지나 정체됐을 때만 차단한다.
     */
    public static String reconcileBlockNote(
            SettlementLedger current, String operator, String reason, Instant now, Duration claimTimeout) {
        if (!current.is(SettlementStatus.PAYOUT_IN_PROGRESS)) {
            throw new ConflictException("SETTLEMENT_STATE_CONFLICT", "진행 중인 정산만 재조정할 수 있습니다");
        }
        boolean ownedByOperator = operator.equals(current.payoutOperatorId());
        if (!ownedByOperator) {
            Instant attemptedAt = current.payoutAttemptedAt();
            Instant staleAt = attemptedAt == null ? null : attemptedAt.plus(claimTimeout);
            if (staleAt != null && now.isBefore(staleAt)) {
                throw new ConflictException(
                        "SETTLEMENT_CLAIM_STILL_ACTIVE",
                        "다른 운영자 또는 지급사의 작업이 아직 진행 중입니다 (차단 가능 시각 %s)".formatted(staleAt));
            }
        }
        return SettlementLedger.payoutNote((ownedByOperator ? "담당자 지급 결과 확인 필요: " : "장기 정체 지급 확인 필요: ") + reason);
    }

    /** 대사 갱신이 빗나감 — 그사이 선점이 바뀌었다. */
    public static ConflictException reconcileRejected() {
        return new ConflictException("SETTLEMENT_STATE_CONFLICT", "선점 상태가 변경되어 목록을 다시 확인해야 합니다");
    }

    /** 차단 원장을 어떻게 복구할지. */
    public enum Recovery {
        /** 이미 같은 증빙으로 지급 완료된 원장이다 — 그대로 돌려준다. */
        ALREADY_RECORDED,
        /** 외부 지급을 확인했다 — 증빙과 함께 지급 완료로 기록한다. */
        RECORD_PAID,
        /** 외부 미지급을 확인했다 — 정산 모드에 따라 자동 재시도 큐나 현재 운영자 작업으로 되돌린다. */
        RETRY
    }

    public static Recovery recovery(SettlementLedger current, boolean alreadyPaid, String paymentReference) {
        if (!current.is(SettlementStatus.PAYOUT_BLOCKED)) {
            if (alreadyPaid
                    && current.is(SettlementStatus.PAID)
                    && paymentReference != null
                    && paymentReference.trim().equals(current.paymentReference())) {
                return Recovery.ALREADY_RECORDED;
            }
            throw new ConflictException("SETTLEMENT_STATE_CONFLICT", "운영 확인 필요 상태의 정산만 복구할 수 있습니다");
        }
        if (!alreadyPaid) {
            return Recovery.RETRY;
        }
        if (paymentReference == null || paymentReference.isBlank()) {
            throw new ConflictException("SETTLEMENT_REFERENCE_REQUIRED", "외부 지급을 확인한 증빙 번호를 입력해야 합니다");
        }
        return Recovery.RECORD_PAID;
    }

    public static String externalPaidNote(String reason) {
        return SettlementLedger.payoutNote("외부 지급 확인 완료: " + reason);
    }

    public static String providerRetryNote(String operator, String reason) {
        return SettlementLedger.payoutNote("외부 미지급 확인 후 자동 재시도 요청 (%s): %s".formatted(operator, reason));
    }

    public static String manualRetryNote(String reason) {
        return SettlementLedger.payoutNote("외부 미지급 확인 후 수동 복구: " + reason);
    }

    /** 복구 갱신이 빗나감 — 그사이 차단 상태가 풀렸다. */
    public static ConflictException recoveryRejected() {
        return new ConflictException("SETTLEMENT_STATE_CONFLICT", "복구 중 상태가 변경되어 목록을 다시 확인해야 합니다");
    }

    /**
     * 지급 완료 갱신이 빗나갔을 때. 같은 증빙으로 이미 완료됐으면 정상 반환(멱등)하고, 아니면 거부 사유를 던진다.
     *
     * @param paymentReference {@link #requirePaymentReference}로 다듬은 증빙 번호
     */
    public static void requireSamePaidEvidence(SettlementLedger current, String paymentReference) {
        if (current.is(SettlementStatus.PAID)) {
            if (paymentReference.equals(current.paymentReference())) {
                return;
            }
            throw new ConflictException("SETTLEMENT_ALREADY_PAID", "이미 다른 지급 증빙 번호로 완료된 정산입니다");
        }
        if (current.is(SettlementStatus.PAYOUT_IN_PROGRESS)) {
            throw new ConflictException("SETTLEMENT_CLAIM_OWNER_MISMATCH", "이 정산을 배정받은 운영자만 지급 완료할 수 있습니다");
        }
        throw new ConflictException("SETTLEMENT_CLAIM_REQUIRED", "외부 이체 전에 먼저 정산 작업을 배정받아야 합니다");
    }

    /** 같은 증빙 번호가 다른 정산에 이미 쓰였다(유일 인덱스). */
    public static ConflictException duplicateReference() {
        return new ConflictException("SETTLEMENT_REFERENCE_DUPLICATE", "이미 다른 정산에 사용된 지급 증빙 번호입니다");
    }
}
