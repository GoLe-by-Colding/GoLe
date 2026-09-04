package com.gole.api.media.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 객체 스토리지 삭제를 멱등 재시도하기 위한 영구 outbox/journal 항목. */
public record MediaDeletionTask(
        String id,
        String mediaKey,
        Status status,
        int attempts,
        Instant nextAttemptAt,
        String lastErrorCode,
        Instant createdAt,
        Instant completedAt) {

    public enum Status {
        PENDING,
        COMPLETED,
        DEAD_LETTER
    }

    public MediaDeletionTask {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(mediaKey, "mediaKey");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static MediaDeletionTask pending(String key, Instant now) {
        return new MediaDeletionTask(UUID.randomUUID().toString(), key, Status.PENDING, 0, now, null, now, null);
    }

    public MediaDeletionTask complete(Instant now) {
        return new MediaDeletionTask(id, mediaKey, Status.COMPLETED, attempts + 1, now, null, createdAt, now);
    }

    public MediaDeletionTask retry(Instant now, Duration delay, String errorCode, int maximumAttempts) {
        int nextAttempts = attempts + 1;
        Status nextStatus = nextAttempts >= maximumAttempts ? Status.DEAD_LETTER : Status.PENDING;
        return new MediaDeletionTask(
                id, mediaKey, nextStatus, nextAttempts, now.plus(delay), errorCode, createdAt, null);
    }
}
