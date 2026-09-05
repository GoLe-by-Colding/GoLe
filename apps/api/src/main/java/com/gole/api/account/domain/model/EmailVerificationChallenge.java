package com.gole.api.account.domain.model;

import java.time.Instant;
import java.util.Objects;

/** Mongo에 보관하는 이메일 인증 challenge. 인증번호 원문 대신 단방향 hash만 가진다. */
public record EmailVerificationChallenge(String codeHash, Instant issuedAt) {

    public EmailVerificationChallenge {
        if (codeHash == null || codeHash.isBlank()) {
            throw new IllegalArgumentException("verification code hash must not be blank");
        }
        Objects.requireNonNull(issuedAt, "issuedAt");
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(issuedAt.plus(VerificationCode.TTL));
    }
}
