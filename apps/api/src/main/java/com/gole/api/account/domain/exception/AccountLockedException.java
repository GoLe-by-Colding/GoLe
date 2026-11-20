package com.gole.api.account.domain.exception;

import com.gole.api.common.exception.UnauthorizedException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 요구사항 1.8: 연속 로그인 실패로 일시 잠금된 계정.
 */
public class AccountLockedException extends UnauthorizedException {

    /** 화면에 그대로 보이는 안내라 한국 시각으로 알린다. */
    private static final DateTimeFormatter KST_TIME =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Asia/Seoul"));

    public AccountLockedException(Instant lockedUntil) {
        super("ACCOUNT_LOCKED", "로그인 시도가 많아 계정을 잠시 잠갔습니다. " + KST_TIME.format(lockedUntil) + " 이후에 다시 시도해 주세요");
    }
}
