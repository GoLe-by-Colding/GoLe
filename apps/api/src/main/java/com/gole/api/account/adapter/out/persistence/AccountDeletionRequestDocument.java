package com.gole.api.account.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 탈퇴 운영 원장. 완료 영수증에는 탈퇴 대상 accountId·이메일·자유서술을 남기지 않는다.
 */
@Document(collection = "account_deletion_requests")
@CompoundIndexes({
    @CompoundIndex(
            name = "active_account_deletion_unique_idx",
            def = "{'accountId': 1}",
            unique = true,
            partialFilter = "{'accountId': {'$type': 'string'}}"),
    @CompoundIndex(name = "deletion_status_updated_idx", def = "{'status': 1, 'updatedAt': -1}")
})
public class AccountDeletionRequestDocument {

    @Id
    private String id;

    private String accountId;
    private String status;

    @Indexed(unique = true)
    private String requestIdempotencyKeyHash;

    private String requestFingerprint;
    private List<String> blockers;
    private String holdReason;
    private String holdPlacedBy;
    private Instant holdPlacedAt;
    private Instant requestedAt;
    private Instant updatedAt;
    private Instant completedAt;
    private String completedBy;
    private String completionIdempotencyKeyHash;
    private String completionFingerprint;
    private Map<String, Long> deletionCounts;

    @Version
    private Long version;

    protected AccountDeletionRequestDocument() {}

    public AccountDeletionRequestDocument(
            String id,
            String accountId,
            String status,
            String requestIdempotencyKeyHash,
            String requestFingerprint,
            List<String> blockers,
            String holdReason,
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
        this.id = id;
        this.accountId = accountId;
        this.status = status;
        this.requestIdempotencyKeyHash = requestIdempotencyKeyHash;
        this.requestFingerprint = requestFingerprint;
        this.blockers = blockers;
        this.holdReason = holdReason;
        this.holdPlacedBy = holdPlacedBy;
        this.holdPlacedAt = holdPlacedAt;
        this.requestedAt = requestedAt;
        this.updatedAt = updatedAt;
        this.completedAt = completedAt;
        this.completedBy = completedBy;
        this.completionIdempotencyKeyHash = completionIdempotencyKeyHash;
        this.completionFingerprint = completionFingerprint;
        this.deletionCounts = deletionCounts;
        this.version = version;
    }

    public String getId() {
        return id;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getStatus() {
        return status;
    }

    public String getRequestIdempotencyKeyHash() {
        return requestIdempotencyKeyHash;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public List<String> getBlockers() {
        return blockers;
    }

    public String getHoldReason() {
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
