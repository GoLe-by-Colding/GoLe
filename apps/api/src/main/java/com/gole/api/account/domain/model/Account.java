package com.gole.api.account.domain.model;

import com.gole.api.account.domain.exception.AccountLockedException;
import com.gole.api.account.domain.exception.AccountSuspendedException;
import com.gole.api.account.domain.exception.VerificationException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * 계정 애그리거트 루트. 인증 상태/로그인 잠금 등 불변식을 캡슐화한다.
 * 프레임워크에 의존하지 않는 순수 도메인.
 */
public final class Account {

    private static final int MAX_FAILED_ATTEMPTS = 5; // 요구사항 1.8
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final int MAX_VERIFICATION_ATTEMPTS = 5;

    private final String id;
    private final Email email;
    private PasswordHash passwordHash;
    private AccountStatus status;
    private Role role;
    private VerificationCode verificationCode; // nullable (검증 완료 시 제거)
    private int verificationFailedAttempts;
    private int failedAttempts;
    private Instant failureWindowStartedAt; // nullable
    private Instant lockedUntil; // nullable
    private String suspendedReason; // nullable (SUSPENDED일 때만 의미 있음)
    private OnboardingProfile onboarding; // never null — 미착수 계정은 empty() (onboarding R1)

    /** 영속성 복원용 생성자. */
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
        this(
                id,
                email,
                passwordHash,
                status,
                role,
                verificationCode,
                failedAttempts,
                failureWindowStartedAt,
                lockedUntil,
                null);
    }

    /** 영속성 복원용 전체 생성자(정지 사유 포함). */
    public Account(
            String id,
            Email email,
            PasswordHash passwordHash,
            AccountStatus status,
            Role role,
            VerificationCode verificationCode,
            int failedAttempts,
            Instant failureWindowStartedAt,
            Instant lockedUntil,
            String suspendedReason) {
        this(
                id,
                email,
                passwordHash,
                status,
                role,
                verificationCode,
                0,
                failedAttempts,
                failureWindowStartedAt,
                lockedUntil,
                suspendedReason);
    }

    /** 영속성 복원용 전체 생성자(인증 실패 횟수 포함). */
    public Account(
            String id,
            Email email,
            PasswordHash passwordHash,
            AccountStatus status,
            Role role,
            VerificationCode verificationCode,
            int verificationFailedAttempts,
            int failedAttempts,
            Instant failureWindowStartedAt,
            Instant lockedUntil,
            String suspendedReason) {
        this(
                id,
                email,
                passwordHash,
                status,
                role,
                verificationCode,
                verificationFailedAttempts,
                failedAttempts,
                failureWindowStartedAt,
                lockedUntil,
                suspendedReason,
                OnboardingProfile.empty());
    }

    /** 영속성 복원용 전체 생성자(온보딩 프로필 포함). (onboarding R1) */
    public Account(
            String id,
            Email email,
            PasswordHash passwordHash,
            AccountStatus status,
            Role role,
            VerificationCode verificationCode,
            int verificationFailedAttempts,
            int failedAttempts,
            Instant failureWindowStartedAt,
            Instant lockedUntil,
            String suspendedReason,
            OnboardingProfile onboarding) {
        this.id = Objects.requireNonNull(id, "id");
        this.email = Objects.requireNonNull(email, "email");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.status = Objects.requireNonNull(status, "status");
        this.role = Objects.requireNonNull(role, "role");
        this.verificationCode = verificationCode;
        this.verificationFailedAttempts = verificationFailedAttempts;
        this.failedAttempts = failedAttempts;
        this.failureWindowStartedAt = failureWindowStartedAt;
        this.lockedUntil = lockedUntil;
        this.suspendedReason = suspendedReason;
        this.onboarding = onboarding == null ? OnboardingProfile.empty() : onboarding;
    }

    /** 신규 가입: 미인증 + 일반(USER) 권한 + 인증코드 발급 상태로 생성. (요구사항 1.1) */
    public static Account register(String id, Email email, PasswordHash passwordHash, VerificationCode code) {
        return new Account(id, email, passwordHash, AccountStatus.UNVERIFIED, Role.USER, code, 0, null, null);
    }

    /**
     * 운영 시드/부트스트랩용: 인증 완료 상태로 지정 권한 계정을 생성한다.
     *
     * <p>구글 소셜 신규가입({@code SocialAccountProvisioner})도 이 팩토리를 함께 쓴다 — 그쪽은
     * 온보딩 D7에 따라 legacyExempt=false(일반 온보딩 대상)여야 하므로, 여기서 면제를
     * 기본값으로 바꾸면 안 된다. 운영 계정 전용 면제는 {@link #operationalBootstrap}을 쓴다.
     */
    public static Account provisioned(String id, Email email, PasswordHash passwordHash, Role role) {
        return new Account(id, email, passwordHash, AccountStatus.VERIFIED, role, null, 0, null, null);
    }

    /**
     * 관리자 등 운영 부트스트랩 전용: {@link #provisioned}과 같지만 애초에 소비자 가입
     * 절차(이메일 인증 → 로그인 → 온보딩 위저드)를 거치지 않는 계정이라 legacyExempt를
     * 처음부터 켜 둔다. 그렇지 않으면 새로 만든 관리자 계정도 매물등록·구매·채팅에서
     * 온보딩 위저드에 붙잡힌다.
     */
    public static Account operationalBootstrap(String id, Email email, PasswordHash passwordHash, Role role) {
        return new Account(
                id,
                email,
                passwordHash,
                AccountStatus.VERIFIED,
                role,
                null,
                0,
                0,
                null,
                null,
                null,
                OnboardingProfile.exempt());
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
        if (verificationFailedAttempts >= MAX_VERIFICATION_ATTEMPTS) {
            throw new VerificationException("VERIFICATION_TOO_MANY_ATTEMPTS", "인증 시도가 초과되었습니다. 새 인증 코드를 요청해 주세요");
        }
        if (!verificationCode.matches(candidateCode)) {
            verificationFailedAttempts++;
            if (verificationFailedAttempts >= MAX_VERIFICATION_ATTEMPTS) {
                throw new VerificationException("VERIFICATION_TOO_MANY_ATTEMPTS", "인증 시도가 초과되었습니다. 새 인증 코드를 요청해 주세요");
            }
            throw new VerificationException("VERIFICATION_CODE_MISMATCH", "Verification code does not match");
        }
        this.status = AccountStatus.VERIFIED;
        this.verificationCode = null;
        this.verificationFailedAttempts = 0;
    }

    /** 인증 대기 계정에 새 코드를 발급한다. 과도한 재요청은 60초 동안 차단한다. */
    public void reissueVerificationCode(VerificationCode code, Instant now) {
        if (status != AccountStatus.UNVERIFIED) {
            throw new VerificationException("ALREADY_VERIFIED", "Email is already verified");
        }
        if (verificationCode != null && now.isBefore(verificationCode.issuedAt().plusSeconds(60))) {
            throw new VerificationException("VERIFICATION_RESEND_TOO_SOON", "인증 코드는 60초 후 다시 요청할 수 있습니다");
        }
        this.verificationCode = Objects.requireNonNull(code, "code");
        this.verificationFailedAttempts = 0;
    }

    /**
     * 운영자 정지. (admin-console 요구사항 6.2)
     *
     * <p>일시 잠금(연속 실패)과 달리 시간 경과로 해제되지 않는다. 사유는 필수로 보관해 분쟁에 대응한다.
     */
    public void suspend(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        this.status = AccountStatus.SUSPENDED;
        this.suspendedReason = reason;
    }

    /**
     * 정지 해제. (admin-console 요구사항 6.6)
     *
     * <p>정지 해제 즉시 로그인할 수 있어야 하므로 실패 카운터·일시 잠금도 함께 초기화한다.
     */
    public void reinstate() {
        this.status = AccountStatus.VERIFIED;
        this.suspendedReason = null;
        recordSuccessfulSignIn();
    }

    /** 권한 변경. (admin-console 요구사항 6.7) */
    public void changeRole(Role newRole) {
        this.role = Objects.requireNonNull(newRole, "newRole");
    }

    // --- 온보딩 (onboarding R1~R7) ---
    // 각 단계는 성공하는 즉시 저장된다(D1). 끝에서 한 번에 저장하면 중간 이탈한 사용자가
    // 다음 로그인에서 처음부터 다시 해야 한다.

    /** 닉네임 설정/변경. 유일성은 애그리거트 밖(리포지토리)에서 확인한다. (R3) */
    public void changeNickname(Nickname nickname) {
        this.onboarding = onboarding.withNickname(Objects.requireNonNull(nickname, "nickname"));
    }

    /** 전화번호 소유 확인 완료. 인증 시각까지 함께 남겨야 "인증됨"으로 센다. (R5) */
    public void markPhoneVerified(PhoneNumber phoneNumber, Instant verifiedAt) {
        this.onboarding = onboarding.withVerifiedPhone(
                Objects.requireNonNull(phoneNumber, "phoneNumber"), Objects.requireNonNull(verifiedAt, "verifiedAt"));
    }

    /** 관심 태그 선택. 개수·목록 검증은 {@link InterestTagCatalog}가 이미 마쳤다고 본다. (R6) */
    public void selectInterestTags(Set<String> tags) {
        this.onboarding = onboarding.withInterestTags(tags);
    }

    /**
     * 약관 동의 기록. 개인정보 동의는 필수라 거부하면 예외, 마케팅 동의는 선택이다. (R7)
     *
     * <p>동의 철회 시 타임스탬프를 지우기 위해 마케팅 쪽은 {@code false}도 받는다.
     */
    public void consent(boolean privacyAgreed, boolean marketingAgreed, Instant now) {
        if (!privacyAgreed) {
            throw new VerificationException("PRIVACY_CONSENT_REQUIRED", "개인정보 수집·이용 동의는 필수입니다");
        }
        Objects.requireNonNull(now, "now");
        Instant privacyAt = onboarding.privacyConsentedAt() == null ? now : onboarding.privacyConsentedAt();
        Instant marketingAt = marketingAgreed
                ? (onboarding.marketingConsentedAt() == null ? now : onboarding.marketingConsentedAt())
                : null;
        this.onboarding = onboarding.withConsents(privacyAt, marketingAt);
    }

    /** 이 스펙 배포 이전 가입자 표시. 1회성 마이그레이션 전용. (D6, R10) */
    public void markLegacyExempt() {
        this.onboarding = onboarding.asLegacyExempt();
    }

    /** 온보딩을 더 요구해야 하는가. 저장된 플래그가 아니라 필드 조합에서 파생한다. (D1) */
    public boolean isOnboardingRequired() {
        return onboarding.isRequired();
    }

    public OnboardingProfile getOnboarding() {
        return onboarding;
    }

    public Nickname getNickname() {
        return onboarding.nickname();
    }

    public PhoneNumber getPhoneNumber() {
        return onboarding.phoneNumber();
    }

    public Instant getPhoneVerifiedAt() {
        return onboarding.phoneVerifiedAt();
    }

    public Set<String> getInterestTags() {
        return onboarding.interestTags();
    }

    public Instant getPrivacyConsentedAt() {
        return onboarding.privacyConsentedAt();
    }

    public Instant getMarketingConsentedAt() {
        return onboarding.marketingConsentedAt();
    }

    public boolean isLegacyExempt() {
        return onboarding.legacyExempt();
    }

    public boolean isSuspended() {
        return status == AccountStatus.SUSPENDED;
    }

    /** 로그인 시도 전 정지 확인. (admin-console 요구사항 6.4) */
    public void ensureNotSuspended() {
        if (isSuspended()) {
            throw new AccountSuspendedException(suspendedReason);
        }
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
        if (failureWindowStartedAt == null || now.isAfter(failureWindowStartedAt.plus(FAILURE_WINDOW))) {
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

    public int getVerificationFailedAttempts() {
        return verificationFailedAttempts;
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

    public String getSuspendedReason() {
        return suspendedReason;
    }
}
