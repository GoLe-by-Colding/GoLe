package com.gole.api.chat.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** 방 ID·요청자 ID·문의 제목·본문을 갖지 않는 durable Discord 알림 원장. */
@Document(collection = "support_notification_outbox")
@CompoundIndexes({
    @CompoundIndex(
            name = "support_notification_due_idx",
            def = "{'state': 1, 'nextAttemptAt': 1, 'leaseUntil': 1, 'createdAt': 1}"),
    @CompoundIndex(name = "support_notification_delivered_idx", def = "{'state': 1, 'deliveredAt': -1}")
})
public class SupportNotificationOutboxDocument {

    @Id
    private String eventId;

    private String type;
    private String supportCategory;
    private String ticketStatus;
    private String state;
    private int attempts;
    private Instant nextAttemptAt;
    private String leaseToken;
    private Instant leaseUntil;
    private String lastErrorCode;
    private Instant occurredAt;
    private Instant createdAt;
    private Instant deliveredAt;

    /** 완료·dead-letter 운영 영수증은 설정된 짧은 운영 보존기간 뒤 Mongo TTL로 제거한다. */
    @Indexed(name = "support_notification_terminal_ttl", expireAfter = "0s")
    private Instant expiresAt;

    protected SupportNotificationOutboxDocument() {}

    public SupportNotificationOutboxDocument(
            String eventId,
            String type,
            String supportCategory,
            String ticketStatus,
            String state,
            int attempts,
            Instant nextAttemptAt,
            String leaseToken,
            Instant leaseUntil,
            String lastErrorCode,
            Instant occurredAt,
            Instant createdAt,
            Instant deliveredAt,
            Instant expiresAt) {
        this.eventId = eventId;
        this.type = type;
        this.supportCategory = supportCategory;
        this.ticketStatus = ticketStatus;
        this.state = state;
        this.attempts = attempts;
        this.nextAttemptAt = nextAttemptAt;
        this.leaseToken = leaseToken;
        this.leaseUntil = leaseUntil;
        this.lastErrorCode = lastErrorCode;
        this.occurredAt = occurredAt;
        this.createdAt = createdAt;
        this.deliveredAt = deliveredAt;
        this.expiresAt = expiresAt;
    }

    public String getEventId() {
        return eventId;
    }

    public String getType() {
        return type;
    }

    public String getSupportCategory() {
        return supportCategory;
    }

    public String getTicketStatus() {
        return ticketStatus;
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

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
