package com.gole.api.account.domain.exception;

import com.gole.api.common.exception.ForbiddenException;

/**
 * admin-console 요구사항 6.4: 운영자가 정지시킨 계정의 로그인 시도.
 *
 * <p>일시 잠금({@link AccountLockedException})과 달리 시간이 지나도 자동 해제되지 않으며,
 * 운영자의 정지 해제로만 복구된다.
 */
public class AccountSuspendedException extends ForbiddenException {

    public AccountSuspendedException(String reason) {
        super("ACCOUNT_SUSPENDED", reason == null || reason.isBlank() ? "정지된 계정입니다" : "정지된 계정입니다: " + reason);
    }
}
