package com.gole.api.notification.domain.model;

import java.time.Instant;
import java.util.Objects;

/** 전화번호를 저장하지 않는 관심태그 알림톡 2단계 아웃박스 이벤트. */
public record InterestTagAlimtalkEvent(
        String eventId,
        Type type,
        State state,
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
        long enqueuedRecipients) {

    public InterestTagAlimtalkEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(listingId, "listingId");
        Objects.requireNonNull(sellerId, "sellerId");
        Objects.requireNonNull(interestTagKey, "interestTagKey");
        Objects.requireNonNull(interestTagLabel, "interestTagLabel");
        Objects.requireNonNull(listingTitle, "listingTitle");
        if ((state == State.PENDING || state == State.IN_FLIGHT) && nextAttemptAt == null) {
            throw new IllegalArgumentException("non-terminal interest-tag alimtalk event requires nextAttemptAt");
        }
        if (state == State.IN_FLIGHT && (leaseToken == null || leaseUntil == null)) {
            throw new IllegalArgumentException("in-flight interest-tag alimtalk event requires a lease");
        }
        if (type == Type.DELIVERY && (recipientAccountId == null || recipientAccountId.isBlank())) {
            throw new IllegalArgumentException("delivery event requires recipientAccountId");
        }
        if (type == Type.FANOUT && recipientAccountId != null) {
            throw new IllegalArgumentException("fanout event cannot have recipientAccountId");
        }
        if (attempts < 0 || enqueuedRecipients < 0) {
            throw new IllegalArgumentException("attempt and recipient counts must not be negative");
        }
    }

    public static InterestTagAlimtalkEvent fanout(
            String listingId,
            String sellerId,
            String interestTagKey,
            String interestTagLabel,
            String listingTitle,
            Instant occurredAt,
            Instant createdAt) {
        return new InterestTagAlimtalkEvent(
                "fanout:" + listingId,
                Type.FANOUT,
                State.PENDING,
                0,
                createdAt,
                null,
                null,
                null,
                occurredAt,
                createdAt,
                null,
                listingId,
                sellerId,
                interestTagKey,
                interestTagLabel,
                listingTitle,
                null,
                null,
                0);
    }

    public static InterestTagAlimtalkEvent delivery(
            InterestTagAlimtalkEvent fanout, String recipientAccountId, Instant createdAt) {
        if (fanout.type != Type.FANOUT) {
            throw new IllegalArgumentException("delivery can only be created from a fanout event");
        }
        return new InterestTagAlimtalkEvent(
                fanout.listingId + ":" + recipientAccountId,
                Type.DELIVERY,
                State.PENDING,
                0,
                createdAt,
                null,
                null,
                null,
                fanout.occurredAt,
                createdAt,
                null,
                fanout.listingId,
                fanout.sellerId,
                fanout.interestTagKey,
                fanout.interestTagLabel,
                fanout.listingTitle,
                recipientAccountId,
                null,
                0);
    }

    public enum Type {
        FANOUT,
        DELIVERY
    }

    public enum State {
        PENDING,
        IN_FLIGHT,
        DELIVERED,
        SKIPPED,
        DEAD_LETTER
    }
}
