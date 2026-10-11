package com.gole.api.account.application.service;

import com.gole.api.account.application.port.in.GetAccountStandingUseCase;
import com.gole.api.account.application.port.out.AccountRepositoryPort;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** 계정 자격 조회. 비밀번호 해시·이메일 같은 계정 정보는 내주지 않고 판단에 필요한 플래그만 낸다. */
@Service
public class AccountStandingService implements GetAccountStandingUseCase {

    private final AccountRepositoryPort accounts;

    public AccountStandingService(AccountRepositoryPort accounts) {
        this.accounts = accounts;
    }

    @Override
    public Optional<AccountStanding> standing(String accountId) {
        return accounts.findById(accountId)
                .map(account -> new AccountStanding(
                        account.getId(), account.isAdmin(), account.isVerified(), account.isSuspended()));
    }
}
