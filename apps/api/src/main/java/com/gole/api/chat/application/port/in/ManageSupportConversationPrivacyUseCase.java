package com.gole.api.chat.application.port.in;

import com.gole.api.chat.domain.model.SupportPurgeReceipt;
import com.gole.api.chat.domain.model.SupportRetentionHold;

/** Inbound port: 해결된 문의 대화의 파기와 보존 보류. 확인 값은 경로의 문의 방 ID와 정확히 일치해야 한다. */
public interface ManageSupportConversationPrivacyUseCase {

    PurgeOutcome purge(
            String roomId,
            String actorId,
            String confirmation,
            PurgeReasonCode reasonCode,
            boolean preservationReviewed,
            String idempotencyKey);

    RetentionHoldOutcome placeRetentionHold(
            String roomId, String actorId, String confirmation, RetentionHoldReasonCode reasonCode);

    RetentionHoldOutcome releaseRetentionHold(
            String roomId, String actorId, String confirmation, RetentionReleaseReasonCode reasonCode);

    enum PurgeReasonCode {
        DATA_SUBJECT_REQUEST_FULFILLED,
        RETENTION_PERIOD_EXPIRED,
        DUPLICATE_OR_TEST_CONVERSATION,
        UNNECESSARY_DATA_REMOVED
    }

    enum RetentionHoldReasonCode {
        ACTIVE_TRANSACTION,
        ACTIVE_DISPUTE,
        LEGAL_OBLIGATION,
        REGULATORY_REQUEST,
        SECURITY_INCIDENT
    }

    enum RetentionReleaseReasonCode {
        TRANSACTION_CLOSED,
        DISPUTE_CLOSED,
        LEGAL_RELEASE_APPROVED,
        REGULATORY_REQUEST_CLOSED,
        PLACED_IN_ERROR
    }

    record PurgeOutcome(SupportPurgeReceipt receipt, boolean replayed) {}

    record RetentionHoldOutcome(SupportRetentionHold hold, boolean changed) {}
}
