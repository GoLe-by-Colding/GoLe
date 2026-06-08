package com.gole.api.account.application.port.out;

import com.gole.api.account.domain.model.Account;

/**
 * 세션 토큰 발급 outbound port. (요구사항 1.6)
 */
public interface SessionTokenPort {

    String issue(Account account);
}
