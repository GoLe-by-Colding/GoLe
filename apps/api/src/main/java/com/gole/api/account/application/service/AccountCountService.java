package com.gole.api.account.application.service;

import com.gole.api.account.application.port.in.CountAccountsUseCase;
import com.gole.api.account.application.port.out.AccountCountPort;
import org.springframework.stereotype.Service;

/** 운영 대시보드용 계정 수. */
@Service
public class AccountCountService implements CountAccountsUseCase {

    private final AccountCountPort counts;

    public AccountCountService(AccountCountPort counts) {
        this.counts = counts;
    }

    @Override
    public long estimatedAccountCount() {
        return counts.estimatedAccountCount();
    }
}
