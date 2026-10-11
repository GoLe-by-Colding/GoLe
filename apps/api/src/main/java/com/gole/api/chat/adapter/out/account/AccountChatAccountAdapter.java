package com.gole.api.chat.adapter.out.account;

import com.gole.api.account.application.port.in.GetAccountStandingUseCase;
import com.gole.api.chat.application.port.out.ChatAccountPort;
import com.gole.api.chat.domain.model.ChatAccount;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 계정 컨텍스트 통합 어댑터. 계정 자격을 account 의 인바운드 포트에서 읽어 chat 값으로 환원한다. */
@Component
public class AccountChatAccountAdapter implements ChatAccountPort {

    private final GetAccountStandingUseCase standings;

    public AccountChatAccountAdapter(GetAccountStandingUseCase standings) {
        this.standings = standings;
    }

    @Override
    public Optional<ChatAccount> findById(String accountId) {
        return standings
                .standing(accountId)
                .map(standing -> new ChatAccount(
                        standing.accountId(), standing.admin(), standing.verified(), standing.suspended()));
    }
}
