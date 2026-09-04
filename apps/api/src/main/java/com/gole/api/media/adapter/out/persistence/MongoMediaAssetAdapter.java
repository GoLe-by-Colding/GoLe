package com.gole.api.media.adapter.out.persistence;

import com.gole.api.media.application.port.out.MediaAssetRepositoryPort;
import com.gole.api.media.domain.model.MediaAsset;
import com.gole.api.media.domain.model.MediaAssetStatus;
import com.gole.api.media.domain.model.MediaTargetType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class MongoMediaAssetAdapter implements MediaAssetRepositoryPort {

    private final MediaAssetMongoRepository repository;

    public MongoMediaAssetAdapter(MediaAssetMongoRepository repository) {
        this.repository = repository;
    }

    @Override
    public void insert(MediaAsset asset) {
        repository.insert(toDocument(asset));
    }

    @Override
    public Optional<MediaAsset> findByKey(String key) {
        return repository.findById(key).map(this::toDomain);
    }

    @Override
    public List<MediaAsset> findByKeys(Collection<String> keys) {
        return repository.findAllById(keys).stream().map(this::toDomain).toList();
    }

    @Override
    public List<MediaAsset> findByTarget(MediaTargetType targetType, String targetId) {
        return repository.findByTargetTypeAndTargetId(targetType.name(), targetId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void saveAll(Collection<MediaAsset> assets) {
        repository.saveAll(assets.stream().map(this::toDocument).toList());
    }

    @Override
    public List<MediaAsset> findExpiredStaged(Instant now, int limit) {
        return repository
                .findByStatusAndStagedExpiresAtLessThanEqualOrderByStagedExpiresAtAsc(
                        MediaAssetStatus.STAGED.name(), now, PageRequest.of(0, limit))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private MediaAssetDocument toDocument(MediaAsset asset) {
        return new MediaAssetDocument(
                asset.key(),
                asset.ownerId(),
                asset.contentType(),
                asset.size(),
                asset.status().name(),
                asset.targetType() == null ? null : asset.targetType().name(),
                asset.targetId(),
                asset.createdAt(),
                asset.stagedExpiresAt(),
                asset.publishedAt(),
                asset.revokedAt());
    }

    private MediaAsset toDomain(MediaAssetDocument document) {
        return new MediaAsset(
                document.getKey(),
                document.getOwnerId(),
                document.getContentType(),
                document.getSize(),
                MediaAssetStatus.valueOf(document.getStatus()),
                document.getTargetType() == null ? null : MediaTargetType.valueOf(document.getTargetType()),
                document.getTargetId(),
                document.getCreatedAt(),
                document.getStagedExpiresAt(),
                document.getPublishedAt(),
                document.getRevokedAt());
    }
}
