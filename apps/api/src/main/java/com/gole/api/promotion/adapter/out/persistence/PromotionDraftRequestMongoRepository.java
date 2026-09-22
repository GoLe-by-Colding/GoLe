package com.gole.api.promotion.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PromotionDraftRequestMongoRepository extends MongoRepository<PromotionDraftRequestDocument, String> {

    Optional<PromotionDraftRequestDocument> findFirstBySourceCommitShaAndStatusInOrderByCreatedAtDesc(
            String sourceCommitSha, Collection<String> statuses);

    Optional<PromotionDraftRequestDocument> findFirstBySourceCommitShaIsNullAndStatusInOrderByCreatedAtDesc(
            Collection<String> statuses);

    List<PromotionDraftRequestDocument> findByOrderByCreatedAtDesc(Pageable pageable);
}
