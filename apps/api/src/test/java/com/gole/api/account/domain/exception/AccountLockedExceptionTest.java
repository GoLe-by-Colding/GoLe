package com.gole.api.account.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AccountLockedExceptionTest {

    @Test
    void tellsTheUnlockTimeInKoreanStandardTime() {
        var exception = new AccountLockedException(Instant.parse("2026-11-20T17:52:29Z"));

        assertThat(exception.getCode()).isEqualTo("ACCOUNT_LOCKED");
        assertThat(exception.getMessage())
                .contains("02:52")
                .doesNotContain("2026-11-20T")
                .doesNotContain("Z");
    }
}
