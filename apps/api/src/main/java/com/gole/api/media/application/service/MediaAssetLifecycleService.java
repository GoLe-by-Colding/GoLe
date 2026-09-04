package com.gole.api.media.application.service;

import com.gole.api.media.application.port.in.AuthorizeMediaReadUseCase;
import com.gole.api.media.application.port.in.ManageMediaAssetsUseCase;
import com.gole.api.media.application.port.out.MediaAssetRepositoryPort;
import com.gole.api.media.application.port.out.MediaDeletionOutboxPort;
import com.gole.api.media.domain.exception.ImageNotFoundException;
import com.gole.api.media.domain.exception.InvalidMediaReferenceException;
import com.gole.api.media.domain.model.MediaAsset;
import com.gole.api.media.domain.model.MediaAssetStatus;
import com.gole.api.media.domain.model.MediaDeletionTask;
import com.gole.api.media.domain.model.MediaKey;
import com.gole.api.media.domain.model.MediaTargetType;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 미디어 소유권, single-attach, 공개/폐기 전이를 담당하는 원장 서비스. */
@Service
public class MediaAssetLifecycleService implements ManageMediaAssetsUseCase, AuthorizeMediaReadUseCase {

    private final MediaAssetRepositoryPort assets;
    private final MediaDeletionOutboxPort deletions;
    private final MediaLifecycleProperties properties;
    private final Clock clock;

    public MediaAssetLifecycleService(
            MediaAssetRepositoryPort assets,
            MediaDeletionOutboxPort deletions,
            MediaLifecycleProperties properties,
            Clock clock) {
        this.assets = assets;
        this.deletions = deletions;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void registerStaged(String ownerId, String key, String contentType, long size) {
        if (ownerId == null || ownerId.isBlank() || !MediaKey.isUserKey(key)) {
            throw new InvalidMediaReferenceException();
        }
        Instant now = Instant.now(clock);
        assets.insert(MediaAsset.staged(key, ownerId, contentType, size, now, now.plus(properties.stagedTtl())));
    }

    @Override
    @Transactional
    public void replaceReferences(
            String ownerId,
            MediaTargetType targetType,
            String targetId,
            List<String> requestedKeys,
            boolean publiclyVisible) {
        List<String> keys = requestedKeys == null ? List.of() : List.copyOf(requestedKeys);
        Set<String> unique = new HashSet<>(keys);
        if (unique.size() != keys.size() || keys.stream().anyMatch(key -> !MediaKey.isUserKey(key))) {
            throw new InvalidMediaReferenceException();
        }

        Instant now = Instant.now(clock);
        Map<String, MediaAsset> requested =
                assets.findByKeys(keys).stream().collect(Collectors.toMap(MediaAsset::key, Function.identity()));
        if (requested.size() != keys.size()) {
            throw new InvalidMediaReferenceException();
        }

        List<MediaAsset> current = assets.findByTarget(targetType, targetId);
        for (String key : keys) {
            MediaAsset asset = requested.get(key);
            if (!asset.isUsableStage(ownerId, now) && !asset.isAttachedTo(ownerId, targetType, targetId)) {
                throw new InvalidMediaReferenceException();
            }
        }

        List<MediaAsset> changed = new java.util.ArrayList<>();
        for (MediaAsset asset : current) {
            if (!unique.contains(asset.key())
                    && (asset.status() == MediaAssetStatus.PUBLIC || asset.status() == MediaAssetStatus.PRIVATE)) {
                MediaAsset revoked = asset.revoke(now);
                changed.add(revoked);
                enqueueDeletion(revoked.key(), now);
            }
        }
        for (String key : keys) {
            MediaAsset asset = requested.get(key);
            if (asset.status() == MediaAssetStatus.STAGED) {
                changed.add(asset.attach(targetType, targetId, publiclyVisible, now));
            } else {
                changed.add(asset.withVisibility(publiclyVisible));
            }
        }
        assets.saveAll(changed);
    }

    @Override
    @Transactional
    public void setTargetVisibility(MediaTargetType targetType, String targetId, boolean publiclyVisible) {
        assets.saveAll(assets.findByTarget(targetType, targetId).stream()
                .filter(asset ->
                        asset.status() == MediaAssetStatus.PUBLIC || asset.status() == MediaAssetStatus.PRIVATE)
                .map(asset -> asset.withVisibility(publiclyVisible))
                .toList());
    }

    @Override
    @Transactional
    public void revokeTarget(MediaTargetType targetType, String targetId) {
        Instant now = Instant.now(clock);
        List<MediaAsset> revoked = assets.findByTarget(targetType, targetId).stream()
                .filter(asset ->
                        asset.status() == MediaAssetStatus.PUBLIC || asset.status() == MediaAssetStatus.PRIVATE)
                .map(asset -> asset.revoke(now))
                .toList();
        revoked.forEach(asset -> enqueueDeletion(asset.key(), now));
        assets.saveAll(revoked);
    }

    @Override
    public void requeueDeletion(String key) {
        if (!MediaKey.isUserKey(key)) {
            throw new InvalidMediaReferenceException();
        }
        deletions.requeue(MediaDeletionTask.pending(key, Instant.now(clock)));
    }

    @Override
    public void requireReadable(String key, Optional<String> viewerId) {
        MediaAsset asset = assets.findByKey(key).orElseThrow(() -> new ImageNotFoundException(key));
        if (!asset.canRead(viewerId.orElse(null), Instant.now(clock))) {
            // 존재 여부와 소유권을 외부에 노출하지 않는다.
            throw new ImageNotFoundException(key);
        }
    }

    /** 예약 시간이 지난 미연결 업로드를 공개 차단하고 삭제 journal에 넣는다. */
    @Transactional
    public int revokeExpiredStages() {
        Instant now = Instant.now(clock);
        List<MediaAsset> revoked = assets.findExpiredStaged(now, properties.batchSize()).stream()
                .filter(asset -> asset.status() == MediaAssetStatus.STAGED)
                .map(asset -> asset.revoke(now))
                .toList();
        revoked.forEach(asset -> enqueueDeletion(asset.key(), now));
        assets.saveAll(revoked);
        return revoked.size();
    }

    private void enqueueDeletion(String key, Instant now) {
        deletions.enqueueIfAbsent(MediaDeletionTask.pending(key, now));
    }
}
