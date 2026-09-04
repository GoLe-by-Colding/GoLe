package com.gole.api.account.application.port.out;

import com.gole.api.account.domain.model.AccountDeletionBlocker;
import com.gole.api.account.domain.model.AccountDeletionRequest;
import com.gole.api.account.domain.model.AccountDeletionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 회원 탈퇴 원장과 원자적 연계 파기의 저장소 경계. */
public interface AccountDeletionRepositoryPort {

    AccountDeletionRequest save(AccountDeletionRequest request);

    Optional<AccountDeletionRequest> findById(String requestId);

    Optional<AccountDeletionRequest> findActiveByAccountId(String accountId);

    List<AccountDeletionRequest> findRecent(AccountDeletionStatus status, int limit);

    List<AccountDeletionBlocker> evaluateBlockers(String accountId, boolean explicitHold);

    /** 차단 조건을 트랜잭션 안에서 다시 평가하고, 없을 때에만 계정과 연계 개인정보를 파기한다. */
    AccountDeletionRequest complete(
            String requestId,
            String expectedAccountId,
            String actorId,
            String completionKeyHash,
            String completionFingerprint,
            Instant completedAt);
}
