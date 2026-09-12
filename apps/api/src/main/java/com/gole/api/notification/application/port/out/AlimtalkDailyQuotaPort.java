package com.gole.api.notification.application.port.out;

import java.time.Duration;

/** 계정 단위 관심태그 알림톡 고정 시간창 상한. */
public interface AlimtalkDailyQuotaPort {

    boolean acquire(String accountId, int maximum, Duration window);
}
