package com.gole.api.account.domain.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 회원 탈퇴 운영 원장.
 *
 * <p>요청 진행 중에만 accountId를 보유하며 완료 시 반드시 제거한다. 이메일·확인 코드·자유서술 사유는
 * 처음부터 저장하지 않는다.
 */
public final class AccountDeletionRequest {

    public static final String SUSPENSION_PREFIX = "ACCOUNT_DELETION_REQUEST:";

    private final String id;
    private String accountId;
    private AccountDeletionStatus status;
    private final String requestIdempotencyKeyHash;
    private final String requestFingerprint;
    private List<AccountDeletionBlocker> blockers;
    private AccountDeletionHoldReason holdReason;
    private String holdPlacedBy;
    private Instant holdPlacedAt;
    private final Instant requestedAt;
    private Instant updatedAt;
    private Instant completedAt;
    private String completedBy;
    private String completionIdempotencyKeyHash;
    private String completionFingerprint;
    private Map<String, Long> deletionCounts;
    private Long version;

    public AccountDeletionRequest(
            String id,
            String accountId,
            AccountDeletionStatus status,
            String requestIdempotencyKeyHash,
            String requestFingerprint,
            List<AccountDeletionBlocker> blockers,
            AccountDeletionHoldReason holdReason,
            String holdPlacedBy,
            Instant holdPlacedAt,
            Instant requestedAt,
            Instant updatedAt,
            Instant completedAt,
            String completedBy,
            String completionIdempotencyKeyHash,
            String completionFingerprint,
            Map<String, Long> deletionCounts,
            Long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.accountId = accountId;
        this.status = Objects.requireNonNull(status, "status");
        this.requestIdempotencyKeyHash = Objects.requireNonNull(requestIdempotencyKeyHash, "requestIdempotencyKeyHash");
        this.requestFingerprint = Objects.requireNonNull(requestFingerprint, "requestFingerprint");
        this.blockers = blockers == null ? List.of() : List.copyOf(blockers);
        this.holdReason = holdReason;
        this.holdPlacedBy = holdPlacedBy;
        this.holdPlacedAt = holdPlacedAt;
        this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.completedAt = completedAt;
        this.completedBy = completedBy;
        this.completionIdempotencyKeyHash = completionIdempotencyKeyHash;
        this.completionFingerprint = completionFingerprint;
        this.deletionCounts = deletionCounts == null ? Map.of() : Map.copyOf(deletionCounts);
        this.version = version;
    }

    public static AccountDeletionRequest requested(
            String id,
            String accountId,
            String keyHash,
            String fingerprint,
            List<AccountDeletionBlocker> blockers,
            Instant now) {
        var status =
                blockers == null || blockers.isEmpty() ? AccountDeletionStatus.READY : AccountDeletionStatus.BLOCKED;
        return new AccountDeletionRequest(
                id,
                accountId,
                status,
                keyHash,
                fingerprint,
                blockers,
                null,
                null,
                null,
                now,
                now,
                null,
                null,
                null,
                null,
                Map.of(),
                null);
    }

    public void review(List<AccountDeletionBlocker> currentBlockers, Instant now) {
        ensureActive();
        this.blockers = List.copyOf(currentBlockers);
        this.status = currentBlockers.isEmpty() ? AccountDeletionStatus.READY : AccountDeletionStatus.BLOCKED;
        this.updatedAt = now;
    }

    public void placeHold(AccountDeletionHoldReason reason, String actorId, Instant now) {
        ensureActive();
        this.holdReason = Objects.requireNonNull(reason, "reason");
        this.holdPlacedBy = Objects.requireNonNull(actorId, "actorId");
        this.holdPlacedAt = Objects.requireNonNull(now, "now");
        review(appendHold(blockers), now);
    }

    public void releaseHold(Instant now) {
        ensureActive();
        this.holdReason = null;
        this.holdPlacedBy = null;
        this.holdPlacedAt = null;
        this.blockers = blockers.stream()
                .filter(blocker -> blocker != AccountDeletionBlocker.EXPLICIT_RETENTION_HOLD)
                .toList();
        this.status = blockers.isEmpty() ? AccountDeletionStatus.READY : AccountDeletionStatus.BLOCKED;
        this.updatedAt = now;
    }

    public void complete(String actorId, String keyHash, String fingerprint, Map<String, Long> counts, Instant now) {
        ensureActive();
        if (!blockers.isEmpty()) {
            throw new IllegalStateException("blocked deletion request cannot be completed");
        }
        this.accountId = null;
        this.status = AccountDeletionStatus.COMPLETED;
        this.blockers = List.of();
        this.holdReason = null;
        this.holdPlacedBy = null;
        this.holdPlacedAt = null;
        this.completedBy = Objects.requireNonNull(actorId, "actorId");
        this.completionIdempotencyKeyHash = Objects.requireNonNull(keyHash, "keyHash");
        this.completionFingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        this.deletionCounts = Map.copyOf(new LinkedHashMap<>(counts));
        this.completedAt = now;
        this.updatedAt = now;
    }

    public boolean isHeld() {
        return holdReason != null;
    }

    public boolean requestMatches(String keyHash, String fingerprint) {
        return requestIdempotencyKeyHash.equals(keyHash) && requestFingerprint.equals(fingerprint);
    }

    public boolean completionMatches(String keyHash, String fingerprint) {
        return status == AccountDeletionStatus.COMPLETED
                && Objects.equals(completionIdempotencyKeyHash, keyHash)
                && Objects.equals(completionFingerprint, fingerprint);
    }

    public static String suspensionReason(String requestId) {
        return SUSPENSION_PREFIX + requestId;
    }

    public static boolean isDeletionSuspension(String reason) {
        return reason != null && reason.startsWith(SUSPENSION_PREFIX);
    }

    private void ensureActive() {
        if (status == AccountDeletionStatus.COMPLETED || accountId == null) {
            throw new IllegalStateException("completed deletion request is immutable");
        }
    }

    private static List<AccountDeletionBlocker> appendHold(List<AccountDeletionBlocker> source) {
        if (source.contains(AccountDeletionBlocker.EXPLICIT_RETENTION_HOLD)) {
            return source;
        }
        var result = new java.util.ArrayList<>(source);
        result.add(AccountDeletionBlocker.EXPLICIT_RETENTION_HOLD);
        return List.copyOf(result);
    }

    public String getId() {
        return id;
    }

    public String getAccountId() {
        return accountId;
    }

    public AccountDeletionStatus getStatus() {
        return status;
    }

    public String getRequestIdempotencyKeyHash() {
        return requestIdempotencyKeyHash;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public List<AccountDeletionBlocker> getBlockers() {
        return blockers;
    }

    public AccountDeletionHoldReason getHoldReason() {
        return holdReason;
    }

    public String getHoldPlacedBy() {
        return holdPlacedBy;
    }

    public Instant getHoldPlacedAt() {
        return holdPlacedAt;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getCompletedBy() {
        return completedBy;
    }

    public String getCompletionIdempotencyKeyHash() {
        return completionIdempotencyKeyHash;
    }

    public String getCompletionFingerprint() {
        return completionFingerprint;
    }

    public Map<String, Long> getDeletionCounts() {
        return deletionCounts;
    }

    public Long getVersion() {
        return version;
    }
}
