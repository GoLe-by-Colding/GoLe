package com.gole.api.account.application.port.out;

import com.gole.api.account.domain.model.Email;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** 비밀번호 재설정용 단기 challenge 저장소. 세션·회원가입 인증 코드와 수명주기를 분리한다. */
public interface PasswordResetChallengeStorePort {

    void store(Email email, Challenge challenge, Duration ttl);

    Optional<Challenge> find(Email email);

    /** challenge가 살아 있을 때만 실패 횟수를 원자적으로 증가시킨다. 만료됐으면 -1을 반환한다. */
    int incrementFailedAttempts(Email email);

    /** 저장된 코드 해시가 기대값과 같을 때만 challenge를 원자적으로 1회 소비한다. */
    boolean consume(Email email, String expectedCodeHash);

    void delete(Email email);

    record Challenge(String accountId, String codeHash, Instant issuedAt, int failedAttempts) {}
}
