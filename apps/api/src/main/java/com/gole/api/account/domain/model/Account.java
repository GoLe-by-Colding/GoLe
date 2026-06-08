package com.gole.api.account.domain.model;

import com.gole.api.account.domain.exception.AccountLockedException;
import com.gole.api.account.domain.exception.VerificationException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 계정 애그리거트 루트. 인증 상태/로그인 잠금 등 불변식을 캡슐화한다.
 * 프레임워크에 의존하지 않는 순수 도메인.
 */
public final class Account {

    private static final int MAX_FAILED_ATTEMPTS = 5;     // 요구사항 1.8
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final String id;
    private final Email email;
    private PasswordHash passwordHash;
    private AccountStatus status;
    private final Role role;
    private VerificationCode verificationCode; // nullable (검증 완료 시 제거)
    private int failedAttempts;
    private Instant failureWindowStartedAt; // nullable
    private Instant lockedUntil;            // nullable

    /** 영속성 복원용 전체 생성자. */
    public Account(
            String id,
            Email email,
            PasswordHash passwordHash,
            AccountStatus status,
            Role role,
            VerificationCode verificationCode,
            int failedAttempts,
            Instant failureWindowStartedAt,
            Instant lockedUntil) {
        this.id = Objects.requireNonNull(id, "id");
        this.email = Objects.requireNonNull(email, "email");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.status = Objects.requireNonNull(status, "status");
        this.role = Objects.requireNonNull(role, "role");
        this.verificationCode = verificationCode;
        this.failedAttempts = failedAttempts;
        this.failureWindowStartedAt = failureWindowStartedAt;
        this.lockedUntil = lockedUntil;
    }

    /** 신규 가입: 미인증 + 일반(USER) 권한 + 인증코드 발급 상태로 생성. (요구사항 1.1) */
    public static Account register(
            String id, Email email, PasswordHash passwordHash, VerificationCode code) {
        return new Account(
                id, email, passwordHash, AccountStatus.UNVERIFIED, Role.USER, code, 0, null, null);
    }

    /** 운영 시드/부트스트랩용: 인증 완료 상태로 지정 권한 계정을 생성한다. */
    public static Account provisioned(
            String id, Email email, PasswordHash passwordHash, Role role) {
        return new Account(
                id, email, passwordHash, AccountStatus.VERIFIED, role, null, 0, null, null);
    }

    /** 이메일 인증. 만료(1.5)/불일치 시 예외, 성공 시 VERIFIED 전이(1.4). */
    public void verify(String candidateCode, Instant now) {
        if (status == AccountStatus.VERIFIED) {
            return; // 멱등
        }
        if (verificationCode == null) {
            throw new VerificationException("VERIFICATION_CODE_MISSING", "No verification code issued");
        }
        if (verificationCode.isExpired(now)) {
            throw new VerificationException("VERIFICATION_CODE_EXPIRED", "Verification code has expired");
        }
        if (!verificationCode.matches(candidateCode)) {
            throw new VerificationException("VERIFICATION_CODE_MISMATCH", "Verification code does not match");
        }
        this.status = AccountStatus.VERIFIED;
        this.verificationCode = null;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /** 로그인 시도 전 잠금 확인. (요구사항 1.8) */
    public void ensureNotLocked(Instant now) {
        if (isLocked(now)) {
            throw new AccountLockedException(lockedUntil);
        }
    }

    /** 로그인 실패 기록. 15분 창 내 5회 누적 시 15분 잠금. (요구사항 1.8) */
    public void recordFailedSignIn(Instant now) {
        if (failureWindowStartedAt == null
                || now.isAfter(failureWindowStartedAt.plus(FAILURE_WINDOW))) {
            failureWindowStartedAt = now;
            failedAttempts = 1;
        } else {
            failedAttempts++;
        }
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            lockedUntil = now.plus(LOCK_DURATION);
        }
    }

    /** 로그인 성공 시 실패 카운터/잠금 초기화. */
    public void recordSuccessfulSignIn() {
        failedAttempts = 0;
        failureWindowStartedAt = null;
        lockedUntil = null;
    }

    /**
     * 비밀번호 해시를 더 강한 알고리즘으로 승격한다. (요구사항 1.12)
     *
     * <p>평문 비밀번호 변경이 아니라, 동일 비밀번호의 저장 표현만 교체하는 마이그레이션 연산이다.
     * 로그인 성공 직후 호출되어 레거시 해시를 BCrypt로 점진 전환한다.
     */
    public void upgradePasswordHash(PasswordHash newHash) {
        this.passwordHash = Objects.requireNonNull(newHash, "newHash");
    }

    public boolean isVerified() {
        return status == AccountStatus.VERIFIED;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public Role getRole() {
        return role;
    }

    public String getId() {
        return id;
    }

    public Email getEmail() {
        return email;
    }

    public PasswordHash getPasswordHash() {
        return passwordHash;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public VerificationCode getVerificationCode() {
        return verificationCode;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public Instant getFailureWindowStartedAt() {
        return failureWindowStartedAt;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }
}
