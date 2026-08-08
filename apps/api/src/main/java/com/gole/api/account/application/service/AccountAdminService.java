package com.gole.api.account.application.service;

import com.gole.api.account.application.port.in.ManageAccountsUseCase;
import com.gole.api.account.application.port.out.AccountRepositoryPort;
import com.gole.api.account.application.port.out.SessionStorePort;
import com.gole.api.account.domain.model.Account;
import com.gole.api.account.domain.model.Role;
import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.exception.NotFoundException;
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

    public AccountAdminService(AccountRepositoryPort accountRepository, SessionStorePort sessionStore) {
        this.accountRepository = accountRepository;
        this.sessionStore = sessionStore;
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
        Account account = load(accountId);
        ensureNotSelf(accountId, actorAccountId);
        ensureNotLastAdmin(account);

        account.suspend(reason.trim());
        Account saved = accountRepository.save(account);
        // 요구사항 6.3: 이미 발급된 토큰이 남아 있으면 정지가 실효되지 않으므로 즉시 폐기한다.
        sessionStore.revokeAllForAccount(accountId);
        return toSummary(saved);
    }

    @Override
    public AccountSummary reinstate(String accountId, String actorAccountId) {
        Account account = load(accountId);
        account.reinstate();
        return toSummary(accountRepository.save(account));
    }

    @Override
    public AccountSummary changeRole(String accountId, String actorAccountId, Role newRole) {
        Account account = load(accountId);
        if (account.getRole() == newRole) {
            return toSummary(account); // 멱등
        }
        ensureNotSelf(accountId, actorAccountId);
        if (newRole != Role.ADMIN) {
            ensureNotLastAdmin(account);
        }

        account.changeRole(newRole);
        Account saved = accountRepository.save(account);
        // 요구사항 6.7: 세션에 캐시된 권한이 남지 않도록 폐기하고 재로그인시킨다.
        sessionStore.revokeAllForAccount(accountId);
        return toSummary(saved);
    }

    private Account load(String accountId) {
        return accountRepository
                .findById(accountId)
                .orElseThrow(() -> new NotFoundException("ACCOUNT_NOT_FOUND", "계정을 찾을 수 없습니다"));
    }

    private static void ensureNotSelf(String accountId, String actorAccountId) {
        if (accountId.equals(actorAccountId)) {
            throw new BadRequestException("ADMIN_SELF_TARGET", "자기 자신에게는 이 조치를 할 수 없습니다");
        }
    }

    private void ensureNotLastAdmin(Account target) {
        if (target.getRole() == Role.ADMIN && accountRepository.countByRole(Role.ADMIN) <= 1) {
            throw new BadRequestException("LAST_ADMIN", "마지막 관리자 계정은 정지하거나 강등할 수 없습니다");
        }
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
