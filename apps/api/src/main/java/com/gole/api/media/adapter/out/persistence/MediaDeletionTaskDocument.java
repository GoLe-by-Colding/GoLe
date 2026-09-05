package com.gole.api.media.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** 완료 항목도 지우지 않는 영구 객체 삭제 journal/outbox 문서. */
@Document("media_deletion_journal")
@CompoundIndex(name = "media_deletion_due", def = "{'status': 1, 'nextAttemptAt': 1}")
public class MediaDeletionTaskDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String mediaKey;

    private String status;
    private int attempts;
    private Instant nextAttemptAt;
    private String lastErrorCode;
    private Instant createdAt;
    private Instant completedAt;

    public MediaDeletionTaskDocument() {}

    public MediaDeletionTaskDocument(
            String id,
            String mediaKey,
            String status,
            int attempts,
            Instant nextAttemptAt,
            String lastErrorCode,
            Instant createdAt,
            Instant completedAt) {
        this.id = id;
        this.mediaKey = mediaKey;
        this.status = status;
        this.attempts = attempts;
        this.nextAttemptAt = nextAttemptAt;
        this.lastErrorCode = lastErrorCode;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
    }

    public String getId() {
        return id;
    }

    public String getMediaKey() {
        return mediaKey;
    }

    public String getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
