package com.gole.api.media.application.port.out;

import com.gole.api.media.domain.model.MediaAsset;
import com.gole.api.media.domain.model.MediaTargetType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 미디어 접근 제어 원장 저장 포트. */
public interface MediaAssetRepositoryPort {

    void insert(MediaAsset asset);

    Optional<MediaAsset> findByKey(String key);

    List<MediaAsset> findByKeys(Collection<String> keys);

    List<MediaAsset> findByTarget(MediaTargetType targetType, String targetId);

    void saveAll(Collection<MediaAsset> assets);

    List<MediaAsset> findExpiredStaged(Instant now, int limit);
}
