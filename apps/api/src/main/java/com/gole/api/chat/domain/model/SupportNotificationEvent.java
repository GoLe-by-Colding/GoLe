package com.gole.api.chat.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 문의 원문·방 ID·요청자 식별자를 저장하지 않는 Discord 알림 outbox 이벤트. */
public record SupportNotificationEvent(
        String eventId,
        EventType type,
        SupportCategory supportCategory,
        SupportStatus ticketStatus,
        State state,
        int attempts,
        Instant nextAttemptAt,
        String leaseToken,
        Instant leaseUntil,
        String lastErrorCode,
        Instant occurredAt,
        Instant createdAt,
        Instant deliveredAt) {

    public SupportNotificationEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(supportCategory, "supportCategory");
        Objects.requireNonNull(ticketStatus, "ticketStatus");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(createdAt, "createdAt");
        if ((state == State.PENDING || state == State.IN_FLIGHT) && nextAttemptAt == null) {
            throw new IllegalArgumentException("non-terminal support notification requires nextAttemptAt");
        }
        if (state == State.IN_FLIGHT && (leaseToken == null || leaseUntil == null)) {
            throw new IllegalArgumentException("in-flight support notification requires a lease");
        }
    }

    public static SupportNotificationEvent pending(
            EventType type, SupportCategory category, SupportStatus status, Instant occurredAt, Instant createdAt) {
        return new SupportNotificationEvent(
                UUID.randomUUID().toString(),
                type,
                category,
                status,
                State.PENDING,
                0,
                createdAt,
                null,
                null,
                null,
                occurredAt,
                createdAt,
                null);
    }

    public enum EventType {
        OPENED,
        REQUESTER_REPLIED
    }

    public enum State {
        PENDING,
        IN_FLIGHT,
        DELIVERED,
        DEAD_LETTER
    }
}
