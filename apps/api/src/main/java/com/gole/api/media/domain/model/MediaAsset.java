package com.gole.api.media.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 객체 스토리지 파일과 별도로 보관하는 접근 제어 원장.
 *
 * <p>객체가 MinIO에 남아 있더라도 이 원장이 PUBLIC이 아니면 익명 조회할 수 없다. PUBLIC 자산은
 * 하나의 콘텐츠에만 연결할 수 있어 타인의 업로드 키 재사용과 교차 콘텐츠 연결을 막는다.
 */
public record MediaAsset(
        String key,
        String ownerId,
        String contentType,
        long size,
        MediaAssetStatus status,
        MediaTargetType targetType,
        String targetId,
        Instant createdAt,
        Instant stagedExpiresAt,
        Instant publishedAt,
        Instant revokedAt) {

    public MediaAsset {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(stagedExpiresAt, "stagedExpiresAt");
        if (key.isBlank() || ownerId.isBlank()) {
            throw new IllegalArgumentException("media key and ownerId must not be blank");
        }
        if ((targetType == null) != (targetId == null)) {
            throw new IllegalArgumentException("media target type and id must be set together");
        }
        if ((status == MediaAssetStatus.PUBLIC || status == MediaAssetStatus.PRIVATE) && targetType == null) {
            throw new IllegalArgumentException("attached media must have a target");
        }
    }

    public static MediaAsset staged(
            String key, String ownerId, String contentType, long size, Instant createdAt, Instant stagedExpiresAt) {
        return new MediaAsset(
                key,
                ownerId,
                contentType,
                size,
                MediaAssetStatus.STAGED,
                null,
                null,
                createdAt,
                stagedExpiresAt,
                null,
                null);
    }

    public boolean isUsableStage(String actorId, Instant now) {
        return status == MediaAssetStatus.STAGED && ownerId.equals(actorId) && stagedExpiresAt.isAfter(now);
    }

    public boolean isAttachedTo(String actorId, MediaTargetType expectedType, String expectedId) {
        return (status == MediaAssetStatus.PUBLIC || status == MediaAssetStatus.PRIVATE)
                && ownerId.equals(actorId)
                && targetType == expectedType
                && Objects.equals(targetId, expectedId);
    }

    public boolean canRead(String viewerId, Instant now) {
        if (status == MediaAssetStatus.PUBLIC) {
            return true;
        }
        if (status == MediaAssetStatus.PRIVATE) {
            return viewerId != null && ownerId.equals(viewerId);
        }
        return status == MediaAssetStatus.STAGED
                && viewerId != null
                && ownerId.equals(viewerId)
                && stagedExpiresAt.isAfter(now);
    }

    public MediaAsset attach(MediaTargetType newTargetType, String newTargetId, boolean publiclyVisible, Instant now) {
        return new MediaAsset(
                key,
                ownerId,
                contentType,
                size,
                publiclyVisible ? MediaAssetStatus.PUBLIC : MediaAssetStatus.PRIVATE,
                Objects.requireNonNull(newTargetType, "newTargetType"),
                requireTargetId(newTargetId),
                createdAt,
                stagedExpiresAt,
                now,
                null);
    }

    public MediaAsset withVisibility(boolean publiclyVisible) {
        if (status != MediaAssetStatus.PUBLIC && status != MediaAssetStatus.PRIVATE) {
            return this;
        }
        return new MediaAsset(
                key,
                ownerId,
                contentType,
                size,
                publiclyVisible ? MediaAssetStatus.PUBLIC : MediaAssetStatus.PRIVATE,
                targetType,
                targetId,
                createdAt,
                stagedExpiresAt,
                publishedAt,
                revokedAt);
    }

    public MediaAsset revoke(Instant now) {
        if (status == MediaAssetStatus.REVOKED) {
            return this;
        }
        return new MediaAsset(
                key,
                ownerId,
                contentType,
                size,
                MediaAssetStatus.REVOKED,
                targetType,
                targetId,
                createdAt,
                stagedExpiresAt,
                publishedAt,
                now);
    }

    private static String requireTargetId(String targetId) {
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException("media target id must not be blank");
        }
        return targetId;
    }
}
