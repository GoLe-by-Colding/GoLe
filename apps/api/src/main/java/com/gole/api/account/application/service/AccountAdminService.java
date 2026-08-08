package com.gole.api.account.application.service;

import com.gole.api.account.application.port.in.ManageAccountsUseCase;
import com.gole.api.account.application.port.out.AccountRepositoryPort;
import com.gole.api.account.application.port.out.SessionStorePort;
import com.gole.api.account.domain.model.Account;
import com.gole.api.account.domain.model.Role;
import com.gole.api.common.exception.BadRequestException;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 운영자의 회원 관리 유스케이스. (admin-console 요구사항 6)
 *
 * <p>로그인 경로({@link AccountService})와 분리해, 운영 전용 관심사가 인증 흐름을 오염시키지 않게 한다.
 *
 * <p>운영 잠금(lockout) 방지를 위해 두 가지 가드를 둔다.
 * <ul>
 *   <li>자기 자신 대상 조치 금지 — 실수로 자신의 권한을 잃는 것을 막는다. (6.8)
 *   <li>마지막 ADMIN 정지·강등 금지 — 관리자가 0명이 되어 아무도 복구할 수 없는 상태를 막는다. (6.9)
 * </ul>
 */
@Service
public class AccountAdminService implements ManageAccountsUseCase {

    private static final int MAX_LIMIT = 100;

    private final AccountRepositoryPort accountRepository;
    private final SessionStorePort sessionStore;
    private final AccountAdminTransitionService transitions;

    public AccountAdminService(
            AccountRepositoryPort accountRepository,
            SessionStorePort sessionStore,
            AccountAdminTransitionService transitions) {
        this.accountRepository = accountRepository;
        this.sessionStore = sessionStore;
        this.transitions = transitions;
    }

    @Override
    public List<AccountSummary> list(String emailQuery, int limit) {
        return accountRepository.findRecent(emailQuery, clamp(limit)).stream()
                .map(AccountAdminService::toSummary)
                .toList();
    }

    @Override
    public AccountSummary suspend(String accountId, String actorAccountId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("MODERATION_REASON_REQUIRED", "정지 사유를 입력해야 합니다");
        }
        Account saved = transitions.suspend(accountId, actorAccountId, reason.trim());
        // 요구사항 6.3: 이미 발급된 토큰이 남아 있으면 정지가 실효되지 않으므로 즉시 폐기한다.
        sessionStore.revokeAllForAccount(accountId);
        return toSummary(saved);
    }

    @Override
    public AccountSummary reinstate(String accountId, String actorAccountId) {
        return toSummary(transitions.reinstate(accountId));
    }

    @Override
    public AccountSummary changeRole(String accountId, String actorAccountId, Role newRole) {
        Account before = accountRepository
                .findById(accountId)
                .orElseThrow(() ->
                        new com.gole.api.common.exception.NotFoundException("ACCOUNT_NOT_FOUND", "계정을 찾을 수 없습니다"));
        if (before.getRole() == newRole) {
            return toSummary(before);
        }
        Account saved = transitions.changeRole(accountId, actorAccountId, newRole);
        // 요구사항 6.7: 세션에 캐시된 권한이 남지 않도록 폐기하고 재로그인시킨다.
        sessionStore.revokeAllForAccount(accountId);
        return toSummary(saved);
    }

    private static AccountSummary toSummary(Account account) {
        return new AccountSummary(
                account.getId(),
                account.getEmail().value(),
                account.getRole(),
                account.getStatus(),
                account.getLockedUntil(),
                account.getSuspendedReason());
    }

    private static int clamp(int limit) {
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }
}
