package com.gole.api.account.domain.exception;

import com.gole.api.common.exception.UnauthorizedException;
import java.time.Instant;

/**
 * 요구사항 1.8: 연속 로그인 실패로 일시 잠금된 계정.
 */
public class AccountLockedException extends UnauthorizedException {

    public AccountLockedException(Instant lockedUntil) {
        super("ACCOUNT_LOCKED", "Account is temporarily locked until " + lockedUntil);
    }
}
