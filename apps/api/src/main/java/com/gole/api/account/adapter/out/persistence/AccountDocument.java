package com.gole.api.account.adapter.out.persistence;

import java.time.Instant;
import java.util.Set;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 계정 MongoDB 영속 모델. 순수 도메인 모델(Account)과 분리되어 있으며
 * 매핑은 {@link AccountPersistenceAdapter}가 담당한다.
 */
@Document(collection = "accounts")
public class AccountDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String email;

    private String passwordHash;

    private String status;

    private String role;

    // 인증 코드 단방향 hash(검증 완료 시 null). 원문용 legacy `verificationCode` 필드는 migration에서 제거한다.
    private String verificationCodeHash;
    private Instant verificationCodeIssuedAt;
    private int verificationFailedAttempts;

    private int failedAttempts;
    private Instant failureWindowStartedAt;
    private Instant lockedUntil;

    // 운영자 정지 사유(SUSPENDED일 때만 의미 있음). admin-console 요구사항 6.2
    private String suspendedReason;

    // --- 온보딩 (onboarding R1) ---
    // 완료 플래그는 저장하지 않는다(D1). 아래 값들의 조합에서 파생한다.

    /** 대소문자 무시 유일. 조회는 정규화 필드로 하므로 여기엔 표시용 원본을 담는다. */
    private String nickname;

    /** 유일성 조회·인덱스용 소문자 키. */
    @Indexed(unique = true, sparse = true)
    private String nicknameNormalized;

    private String phoneNumber;
    private Instant phoneVerifiedAt;
    private Set<String> interestTags;
    private Instant privacyConsentedAt;
    private Instant marketingConsentedAt;

    /** 이 스펙 배포 이전 가입자. 파생값이 아니라 마이그레이션이 찍는 사실이다(D6). */
    private boolean legacyExempt;

    protected AccountDocument() {
        // MongoDB 매핑용
    }

    public AccountDocument(
            String id,
            String email,
            String passwordHash,
            String status,
            String role,
            String verificationCodeHash,
            Instant verificationCodeIssuedAt,
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
                verificationCodeHash,
                verificationCodeIssuedAt,
                verificationFailedAttempts,
                failedAttempts,
                failureWindowStartedAt,
                lockedUntil,
                suspendedReason,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false);
    }

    public AccountDocument(
            String id,
            String email,
            String passwordHash,
            String status,
            String role,
            String verificationCodeHash,
            Instant verificationCodeIssuedAt,
            int verificationFailedAttempts,
            int failedAttempts,
            Instant failureWindowStartedAt,
            Instant lockedUntil,
            String suspendedReason,
            String nickname,
            String nicknameNormalized,
            String phoneNumber,
            Instant phoneVerifiedAt,
            Set<String> interestTags,
            Instant privacyConsentedAt,
            Instant marketingConsentedAt,
            boolean legacyExempt) {
        this.nickname = nickname;
        this.nicknameNormalized = nicknameNormalized;
        this.phoneNumber = phoneNumber;
        this.phoneVerifiedAt = phoneVerifiedAt;
        this.interestTags = interestTags;
        this.privacyConsentedAt = privacyConsentedAt;
        this.marketingConsentedAt = marketingConsentedAt;
        this.legacyExempt = legacyExempt;
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.status = status;
        this.role = role;
        this.verificationCodeHash = verificationCodeHash;
        this.verificationCodeIssuedAt = verificationCodeIssuedAt;
        this.verificationFailedAttempts = verificationFailedAttempts;
        this.failedAttempts = failedAttempts;
        this.failureWindowStartedAt = failureWindowStartedAt;
        this.lockedUntil = lockedUntil;
        this.suspendedReason = suspendedReason;
    }

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getStatus() {
        return status;
    }

    public String getRole() {
        return role;
    }

    public String getVerificationCodeHash() {
        return verificationCodeHash;
    }

    public Instant getVerificationCodeIssuedAt() {
        return verificationCodeIssuedAt;
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

    public String getNickname() {
        return nickname;
    }

    public String getNicknameNormalized() {
        return nicknameNormalized;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public Instant getPhoneVerifiedAt() {
        return phoneVerifiedAt;
    }

    public Set<String> getInterestTags() {
        return interestTags;
    }

    public Instant getPrivacyConsentedAt() {
        return privacyConsentedAt;
    }

    public Instant getMarketingConsentedAt() {
        return marketingConsentedAt;
    }

    public boolean isLegacyExempt() {
        return legacyExempt;
    }
}
