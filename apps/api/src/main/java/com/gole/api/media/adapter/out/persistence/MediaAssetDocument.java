package com.gole.api.media.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** MongoDB 미디어 접근 제어 원장 문서. */
@Document("media_assets")
@CompoundIndex(name = "media_target_lookup", def = "{'targetType': 1, 'targetId': 1, 'status': 1}")
public class MediaAssetDocument {

    @Id
    private String key;

    @Indexed
    private String ownerId;

    private String contentType;
    private long size;

    @Indexed
    private String status;

    private String targetType;
    private String targetId;
    private Instant createdAt;

    @Indexed
    private Instant stagedExpiresAt;

    private Instant publishedAt;
    private Instant revokedAt;

    public MediaAssetDocument() {}

    public MediaAssetDocument(
            String key,
            String ownerId,
            String contentType,
            long size,
            String status,
            String targetType,
            String targetId,
            Instant createdAt,
            Instant stagedExpiresAt,
            Instant publishedAt,
            Instant revokedAt) {
        this.key = key;
        this.ownerId = ownerId;
        this.contentType = contentType;
        this.size = size;
        this.status = status;
        this.targetType = targetType;
        this.targetId = targetId;
        this.createdAt = createdAt;
        this.stagedExpiresAt = stagedExpiresAt;
        this.publishedAt = publishedAt;
        this.revokedAt = revokedAt;
    }

    public String getKey() {
        return key;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSize() {
        return size;
    }

    public String getStatus() {
        return status;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStagedExpiresAt() {
        return stagedExpiresAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
