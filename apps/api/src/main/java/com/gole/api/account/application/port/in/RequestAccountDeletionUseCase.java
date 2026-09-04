package com.gole.api.account.application.port.in;

import com.gole.api.account.domain.model.AccountDeletionBlocker;
import com.gole.api.account.domain.model.AccountDeletionStatus;
import java.time.Instant;
import java.util.List;

/** 본인 인증을 거쳐 탈퇴를 요청하고 즉시 계정 접근을 차단한다. */
public interface RequestAccountDeletionUseCase {

    void issueVerification(String accountId);

    Result request(Command command);

    record Command(
            String accountId,
            String emailConfirmation,
            String confirmationPhrase,
            String verificationCode,
            String idempotencyKey) {}

    record Result(
            String requestId,
            AccountDeletionStatus status,
            List<AccountDeletionBlocker> blockers,
            Instant requestedAt) {}
}
