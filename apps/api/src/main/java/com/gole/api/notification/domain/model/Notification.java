package com.gole.api.notification.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 알림 애그리거트. 읽음 상태 전이를 캡슐화한다. 프레임워크 무의존. (알림 스펙 N1, N4)
 */
public final class Notification {

    private final String id;
    private final String recipientId;
    private final NotificationType type;
    private final String message;
    private final String link; // nullable
    private boolean read;
    private final Instant createdAt;

    public Notification(
            String id,
            String recipientId,
            NotificationType type,
            String message,
            String link,
            boolean read,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.recipientId = requireText(recipientId, "recipientId");
        this.type = Objects.requireNonNull(type, "type");
        this.message = requireText(message, "message");
        this.link = link;
        this.read = read;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /** 신규 알림: 읽지 않음 상태로 생성. */
    public static Notification create(
            String id,
            String recipientId,
            NotificationType type,
            String message,
            String link,
            Instant now) {
        return new Notification(id, recipientId, type, message, link, false, now);
    }

    public void markRead() {
        this.read = true;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public String getId() {
        return id;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public String getLink() {
        return link;
    }

    public boolean isRead() {
        return read;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
