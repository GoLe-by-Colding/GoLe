package com.gole.api.account.application.port.in;

import com.gole.api.account.domain.model.Role;
import java.util.Optional;

/**
 * Inbound port: 세션 토큰으로 현재 로그인 사용자를 해석한다. (/api/v1/accounts/me)
 */
public interface GetCurrentSessionUseCase {

    Optional<CurrentSession> resolve(String sessionToken);

    record CurrentSession(String accountId, Role role) {
    }
}
