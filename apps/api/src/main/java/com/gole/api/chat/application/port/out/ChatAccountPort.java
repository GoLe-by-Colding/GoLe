package com.gole.api.chat.application.port.out;

import com.gole.api.chat.domain.model.ChatAccount;
import java.util.Optional;

/** Outbound port: 대화 참여자·관리자의 계정 자격. */
public interface ChatAccountPort {

    Optional<ChatAccount> findById(String accountId);
}
