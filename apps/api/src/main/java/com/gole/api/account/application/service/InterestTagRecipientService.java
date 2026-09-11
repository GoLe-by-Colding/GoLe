package com.gole.api.account.application.service;

import com.gole.api.account.application.port.in.ListInterestTagRecipientsUseCase;
import com.gole.api.account.application.port.out.AccountRepositoryPort;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** 관심태그 알림 대상 조회를 온보딩 변경 유스케이스와 분리한 읽기 서비스. */
@Service
public class InterestTagRecipientService implements ListInterestTagRecipientsUseCase {

    private final AccountRepositoryPort accounts;

    public InterestTagRecipientService(AccountRepositoryPort accounts) {
        this.accounts = accounts;
    }

    @Override
    public List<String> listEligibleAccountIds(String tagKey, String afterAccountId, int limit) {
        return accounts.findMarketingReachableIdsByInterestTag(tagKey, afterAccountId, limit);
    }

    @Override
    public Optional<MarketingRecipient> resolveEligible(String accountId, String tagKey) {
        return accounts.findMarketingRecipient(accountId, tagKey);
    }
}
