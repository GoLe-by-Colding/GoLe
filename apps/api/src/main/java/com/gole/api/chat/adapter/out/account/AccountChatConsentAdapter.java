package com.gole.api.chat.adapter.out.account;

import com.gole.api.account.application.port.in.ManageThirdPartyProvisionConsentUseCase;
import com.gole.api.chat.application.port.out.ChatConsentPort;
import org.springframework.stereotype.Component;

/** 계정 컨텍스트 통합 어댑터. 제3자 제공 동의 판정을 account 의 인바운드 포트에 맡긴다. */
@Component
public class AccountChatConsentAdapter implements ChatConsentPort {

    private final ManageThirdPartyProvisionConsentUseCase consents;

    public AccountChatConsentAdapter(ManageThirdPartyProvisionConsentUseCase consents) {
        this.consents = consents;
    }

    @Override
    public void requireCurrent(String accountId) {
        consents.requireCurrent(accountId);
    }

    @Override
    public void requireCurrentSubject(String accountId) {
        consents.requireCurrentSubject(accountId);
    }
}
