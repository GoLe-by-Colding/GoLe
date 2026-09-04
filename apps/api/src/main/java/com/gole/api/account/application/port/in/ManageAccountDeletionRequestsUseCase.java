package com.gole.api.account.application.port.in;

import com.gole.api.account.domain.model.AccountDeletionBlocker;
import com.gole.api.account.domain.model.AccountDeletionHoldReason;
import com.gole.api.account.domain.model.AccountDeletionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 관리자 전용 탈퇴 보존 검토·파기 유스케이스. */
public interface ManageAccountDeletionRequestsUseCase {

    List<Result> list(AccountDeletionStatus status, int limit, String actorId);

    Result review(String requestId, String actorId);

    Result placeHold(String requestId, String confirmation, AccountDeletionHoldReason reason, String actorId);

    Result releaseHold(String requestId, String confirmation, String actorId);

    Result complete(Command command);

    record Command(
            String requestId,
            String confirmation,
            boolean preservationReviewed,
            String idempotencyKey,
            String actorId) {}

    record Result(
            String requestId,
            AccountDeletionStatus status,
            List<AccountDeletionBlocker> blockers,
            AccountDeletionHoldReason holdReason,
            Instant requestedAt,
            Instant updatedAt,
            Instant completedAt,
            Map<String, Long> deletionCounts) {}
}
