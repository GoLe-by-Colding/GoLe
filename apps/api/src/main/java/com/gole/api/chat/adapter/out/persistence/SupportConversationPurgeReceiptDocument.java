package com.gole.api.chat.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** 문의 원문이나 요청자 식별자를 포함하지 않는 최소 파기 영수증. */
@Document(collection = "support_conversation_purge_receipts")
public class SupportConversationPurgeReceiptDocument {

    @Id
    private String receiptId;

    private String actorId;
    private String reasonCode;

    @Indexed(unique = true)
    private String idempotencyKeyHash;

    private String requestFingerprint;
    private Instant resolvedAt;
    private Instant purgedAt;
    private long messages;
    private long supportTickets;
    private long socialRooms;
    private long assistantAnalyses;
    private long internalNotes;
    private long readCursors;
    private long retentionHolds;
    private long auditReferencesAnonymized;

    protected SupportConversationPurgeReceiptDocument() {}

    public SupportConversationPurgeReceiptDocument(
            String receiptId,
            String actorId,
            String reasonCode,
            String idempotencyKeyHash,
            String requestFingerprint,
            Instant resolvedAt,
            Instant purgedAt,
            long messages,
            long supportTickets,
            long socialRooms,
            long assistantAnalyses,
            long internalNotes,
            long readCursors,
            long retentionHolds,
            long auditReferencesAnonymized) {
        this.receiptId = receiptId;
        this.actorId = actorId;
        this.reasonCode = reasonCode;
        this.idempotencyKeyHash = idempotencyKeyHash;
        this.requestFingerprint = requestFingerprint;
        this.resolvedAt = resolvedAt;
        this.purgedAt = purgedAt;
        this.messages = messages;
        this.supportTickets = supportTickets;
        this.socialRooms = socialRooms;
        this.assistantAnalyses = assistantAnalyses;
        this.internalNotes = internalNotes;
        this.readCursors = readCursors;
        this.retentionHolds = retentionHolds;
        this.auditReferencesAnonymized = auditReferencesAnonymized;
    }

    public String getReceiptId() {
        return receiptId;
    }

    public String getActorId() {
        return actorId;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getIdempotencyKeyHash() {
        return idempotencyKeyHash;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getPurgedAt() {
        return purgedAt;
    }

    public long getMessages() {
        return messages;
    }

    public long getSupportTickets() {
        return supportTickets;
    }

    public long getSocialRooms() {
        return socialRooms;
    }

    public long getAssistantAnalyses() {
        return assistantAnalyses;
    }

    public long getInternalNotes() {
        return internalNotes;
    }

    public long getReadCursors() {
        return readCursors;
    }

    public long getRetentionHolds() {
        return retentionHolds;
    }

    public long getAuditReferencesAnonymized() {
        return auditReferencesAnonymized;
    }
}
