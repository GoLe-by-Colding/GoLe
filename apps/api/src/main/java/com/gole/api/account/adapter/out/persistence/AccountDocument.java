package com.gole.api.account.adapter.out.persistence;

import java.time.Instant;
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

    // 인증 코드(검증 완료 시 null)
    private String verificationCode;
    private Instant verificationCodeIssuedAt;
    private int verificationFailedAttempts;

    private int failedAttempts;
    private Instant failureWindowStartedAt;
    private Instant lockedUntil;

    // 운영자 정지 사유(SUSPENDED일 때만 의미 있음). admin-console 요구사항 6.2
    private String suspendedReason;

    protected AccountDocument() {
        // MongoDB 매핑용
    }

    public AccountDocument(
            String id,
            String email,
            String passwordHash,
            String status,
            String role,
            String verificationCode,
            Instant verificationCodeIssuedAt,
            int verificationFailedAttempts,
            int failedAttempts,
            Instant failureWindowStartedAt,
            Instant lockedUntil,
            String suspendedReason) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.status = status;
        this.role = role;
        this.verificationCode = verificationCode;
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

    public String getVerificationCode() {
        return verificationCode;
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
}
