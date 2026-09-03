package com.gole.api.account.application.port.in;

import com.gole.api.account.domain.model.Role;
import java.time.Duration;
import java.util.Optional;

/** 기존 세션의 유효성을 재검증하고 필요할 때 불투명 토큰을 교체한다. */
public interface RefreshSessionUseCase {

    Optional<RefreshSessionResult> refresh(String currentToken);

    record RefreshSessionResult(
            String accountId, String sessionToken, Role role, boolean rotated, Duration remainingLifetime) {}
}
