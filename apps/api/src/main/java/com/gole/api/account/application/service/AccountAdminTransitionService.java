package com.gole.api.account.application.service;

import com.gole.api.account.application.port.out.AccountRepositoryPort;
import com.gole.api.account.domain.model.Account;
import com.gole.api.account.domain.model.Role;
import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 계정 변경의 Mongo 트랜잭션 경계. Redis 세션 폐기는 호출자가 커밋 뒤 수행한다. */
@Service
public class AccountAdminTransitionService {

    private final AccountRepositoryPort accounts;

    public AccountAdminTransitionService(AccountRepositoryPort accounts) {
        this.accounts = accounts;
    }

    @Transactional
    public Account suspend(String accountId, String actorAccountId, String reason) {
        Account account = load(accountId);
        ensureNotSelf(accountId, actorAccountId);
        if (account.getRole() == Role.ADMIN) {
            accounts.fenceAdminMutation();
            ensureNotLastAdmin();
        }
        account.suspend(reason);
        return accounts.save(account);
    }

    @Transactional
    public Account reinstate(String accountId) {
        Account account = load(accountId);
        account.reinstate();
        return accounts.save(account);
    }

    @Transactional
    public Account changeRole(String accountId, String actorAccountId, Role newRole) {
        Account account = load(accountId);
        if (account.getRole() == newRole) {
            return account;
        }
        ensureNotSelf(accountId, actorAccountId);
        if (account.getRole() == Role.ADMIN && newRole != Role.ADMIN) {
            accounts.fenceAdminMutation();
            ensureNotLastAdmin();
        }
        account.changeRole(newRole);
        return accounts.save(account);
    }

    private Account load(String accountId) {
        return accounts.findById(accountId)
                .orElseThrow(() -> new NotFoundException("ACCOUNT_NOT_FOUND", "계정을 찾을 수 없습니다"));
    }

    private void ensureNotLastAdmin() {
        if (accounts.countByRole(Role.ADMIN) <= 1) {
            throw new BadRequestException("LAST_ADMIN", "마지막 관리자 계정은 정지하거나 강등할 수 없습니다");
        }
    }

    private static void ensureNotSelf(String accountId, String actorAccountId) {
        if (accountId.equals(actorAccountId)) {
            throw new BadRequestException("ADMIN_SELF_TARGET", "자기 자신에게는 이 조치를 할 수 없습니다");
        }
    }
}
