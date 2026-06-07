package com.gole.api.account.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 이메일 인증 코드 값 객체. 발급 시각 기준 10분 유효. (요구사항 1.4, 1.5)
 */
public record VerificationCode(String code, Instant issuedAt) {

    public static final Duration TTL = Duration.ofMinutes(10);

    public VerificationCode {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("verification code must not be blank");
        }
        Objects.requireNonNull(issuedAt, "issuedAt");
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(issuedAt.plus(TTL));
    }

    public boolean matches(String candidate) {
        return code.equals(candidate);
    }
}
