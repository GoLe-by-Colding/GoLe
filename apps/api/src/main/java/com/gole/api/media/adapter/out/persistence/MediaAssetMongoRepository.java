package com.gole.api.media.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

interface MediaAssetMongoRepository extends MongoRepository<MediaAssetDocument, String> {

    List<MediaAssetDocument> findByTargetTypeAndTargetId(String targetType, String targetId);

    List<MediaAssetDocument> findByStatusAndStagedExpiresAtLessThanEqualOrderByStagedExpiresAtAsc(
            String status, Instant now, Pageable pageable);
}
