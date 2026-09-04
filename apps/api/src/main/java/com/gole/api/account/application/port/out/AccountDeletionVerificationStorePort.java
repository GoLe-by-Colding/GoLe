package com.gole.api.account.application.port.out;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** 회원 탈퇴 전용 단기 이메일 challenge. 다른 인증 목적과 키 공간·수명주기를 공유하지 않는다. */
public interface AccountDeletionVerificationStorePort {

    void store(String accountId, Challenge challenge, Duration ttl);

    Optional<Challenge> find(String accountId);

    int incrementFailedAttempts(String accountId);

    boolean consume(String accountId, String expectedCodeHash);

    void delete(String accountId);

    record Challenge(String codeHash, Instant issuedAt, int failedAttempts) {}
}
