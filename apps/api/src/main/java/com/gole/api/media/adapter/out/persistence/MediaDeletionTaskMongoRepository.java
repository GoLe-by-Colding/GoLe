package com.gole.api.media.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

interface MediaDeletionTaskMongoRepository extends MongoRepository<MediaDeletionTaskDocument, String> {

    List<MediaDeletionTaskDocument> findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            String status, Instant now, Pageable pageable);

    List<MediaDeletionTaskDocument> findByStatusAndCompletedAtGreaterThanEqualOrderByCompletedAtAsc(
            String status, Instant since);
}
