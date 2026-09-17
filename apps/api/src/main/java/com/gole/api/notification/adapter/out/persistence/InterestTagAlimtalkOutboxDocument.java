package com.gole.api.notification.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** 전화번호 없이 수신 계정 ID만 보관하는 관심태그 알림톡 아웃박스 문서. */
@Document(collection = "interest_tag_alimtalk_outbox")
@CompoundIndexes({
    @CompoundIndex(name = "itat_due_idx", def = "{'state':1,'nextAttemptAt':1,'leaseUntil':1,'createdAt':1}"),
    @CompoundIndex(name = "itat_listing_idx", def = "{'listingId':1,'type':1}")
})
public class InterestTagAlimtalkOutboxDocument {

    @Id
    private String eventId;

    private String type;
    private String state;
    private int attempts;
    private Instant nextAttemptAt;
    private String leaseToken;
    private Instant leaseUntil;
    private String lastErrorCode;
    private Instant occurredAt;
    private Instant createdAt;
    private Instant completedAt;
    private String listingId;
    private String sellerId;
    private String interestTagKey;
    private String interestTagLabel;
    private String listingTitle;
    private String recipientAccountId;
    private String cursorAccountId;
    private long enqueuedRecipients;

    @Indexed(name = "itat_terminal_ttl", expireAfter = "0s")
    private Instant expiresAt;

    protected InterestTagAlimtalkOutboxDocument() {}

    public InterestTagAlimtalkOutboxDocument(
            String eventId,
            String type,
            String state,
            int attempts,
            Instant nextAttemptAt,
            String leaseToken,
            Instant leaseUntil,
            String lastErrorCode,
            Instant occurredAt,
            Instant createdAt,
            Instant completedAt,
            String listingId,
            String sellerId,
            String interestTagKey,
            String interestTagLabel,
            String listingTitle,
            String recipientAccountId,
            String cursorAccountId,
            long enqueuedRecipients,
            Instant expiresAt) {
        this.eventId = eventId;
        this.type = type;
        this.state = state;
        this.attempts = attempts;
        this.nextAttemptAt = nextAttemptAt;
        this.leaseToken = leaseToken;
        this.leaseUntil = leaseUntil;
        this.lastErrorCode = lastErrorCode;
        this.occurredAt = occurredAt;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
        this.listingId = listingId;
        this.sellerId = sellerId;
        this.interestTagKey = interestTagKey;
        this.interestTagLabel = interestTagLabel;
        this.listingTitle = listingTitle;
        this.recipientAccountId = recipientAccountId;
        this.cursorAccountId = cursorAccountId;
        this.enqueuedRecipients = enqueuedRecipients;
        this.expiresAt = expiresAt;
    }

    public String getEventId() {
        return eventId;
    }

    public String getType() {
        return type;
    }

    public String getState() {
        return state;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLeaseToken() {
        return leaseToken;
    }

    public Instant getLeaseUntil() {
        return leaseUntil;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getListingId() {
        return listingId;
    }

    public String getSellerId() {
        return sellerId;
    }

    public String getInterestTagKey() {
        return interestTagKey;
    }

    public String getInterestTagLabel() {
        return interestTagLabel;
    }

    public String getListingTitle() {
        return listingTitle;
    }

    public String getRecipientAccountId() {
        return recipientAccountId;
    }

    public String getCursorAccountId() {
        return cursorAccountId;
    }

    public long getEnqueuedRecipients() {
        return enqueuedRecipients;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
