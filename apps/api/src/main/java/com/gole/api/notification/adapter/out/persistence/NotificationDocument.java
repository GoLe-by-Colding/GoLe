package com.gole.api.notification.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 알림 MongoDB 영속 모델. 도메인 {@code Notification}과 분리, 매핑은 어댑터가 담당.
 */
@Document(collection = "notifications")
public class NotificationDocument {

    @Id
    private String id;

    @Indexed
    private String recipientId;

    private String type;
    private String message;
    private String link;
    private boolean read;

    @Indexed
    private Instant createdAt;

    protected NotificationDocument() {
        // MongoDB 매핑용
    }

    public NotificationDocument(
            String id,
            String recipientId,
            String type,
            String message,
            String link,
            boolean read,
            Instant createdAt) {
        this.id = id;
        this.recipientId = recipientId;
        this.type = type;
        this.message = message;
        this.link = link;
        this.read = read;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public String getType() {
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
