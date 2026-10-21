package com.gole.api.account.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.account.domain.exception.AccountSuspendedException;
import com.gole.api.account.domain.exception.VerificationException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountStatusTransitionTest {

    private static final Instant NOW = Instant.parse("2026-10-21T00:00:00Z");

    private static Account unverified() {
        return Account.register(
                "pending-account",
                new Email("pending@gole.test"),
                new PasswordHash("password-hash"),
                new EmailVerificationChallenge("verification-hash", NOW));
    }

    @Test
    @DisplayName("정지되지 않은 미인증 계정은 정지 해제로 인증되지 않음")
    void reinstate_keepsUnverifiedAccountUnverified() {
        Account account = unverified();

        account.reinstate();

        assertThat(account.getStatus()).isEqualTo(AccountStatus.UNVERIFIED);
        assertThat(account.getVerificationChallenge()).isNotNull();
    }

    @Test
    @DisplayName("미인증 계정의 정지 해제는 인증 대기 상태와 실패 횟수를 보존함")
    void reinstate_restoresPendingVerification() {
        Account account = unverified();
        assertThatThrownBy(() -> account.verify("wrong", NOW, (candidate, hash) -> false))
                .isInstanceOf(VerificationException.class);
        account.suspend("운영 검토");

        account.reinstate();

        assertThat(account.getStatus()).isEqualTo(AccountStatus.UNVERIFIED);
        assertThat(account.getVerificationFailedAttempts()).isEqualTo(1);
        assertThat(account.getSuspendedReason()).isNull();
        account.verify("correct", NOW, (candidate, hash) -> true);
        assertThat(account.isVerified()).isTrue();
        assertThat(account.getVerificationChallenge()).isNull();
    }

    @Test
    @DisplayName("정지된 미인증 계정은 유효한 인증 코드로 정지를 우회하지 못함")
    void verify_rejectsSuspendedAccountWithoutChangingChallenge() {
        Account account = unverified();
        EmailVerificationChallenge challenge = account.getVerificationChallenge();
        account.suspend("운영 검토");

        assertThatThrownBy(() -> account.verify("correct", NOW, (candidate, hash) -> true))
                .isInstanceOf(AccountSuspendedException.class);

        assertThat(account.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(account.getVerificationChallenge()).isSameAs(challenge);
        assertThat(account.getSuspendedReason()).isEqualTo("운영 검토");
    }

    @Test
    @DisplayName("인증 완료 계정의 정지 해제는 로그인 잠금과 사유를 초기화함")
    void reinstate_restoresVerifiedAccountAndClearsTemporaryLock() {
        Account account = Account.provisioned(
                "verified-account", new Email("verified@gole.test"), new PasswordHash("hash"), Role.USER);
        for (int attempt = 0; attempt < 5; attempt++) account.recordFailedSignIn(NOW);
        account.suspend("운영 검토");

        account.reinstate();

        assertThat(account.isVerified()).isTrue();
        assertThat(account.isLocked(NOW)).isFalse();
        assertThat(account.getFailedAttempts()).isZero();
        assertThat(account.getSuspendedReason()).isNull();
    }

    @Test
    @DisplayName("이미 정상 상태인 계정의 정지 해제는 로그인 실패 잠금을 풀지 않음")
    void reinstate_doesNotUnlockAnAccountThatWasNotSuspended() {
        Account account = Account.provisioned(
                "verified-account", new Email("verified@gole.test"), new PasswordHash("hash"), Role.USER);
        for (int attempt = 0; attempt < 5; attempt++) account.recordFailedSignIn(NOW);

        account.reinstate();

        assertThat(account.isLocked(NOW)).isTrue();
        assertThat(account.getFailedAttempts()).isEqualTo(5);
    }
}
