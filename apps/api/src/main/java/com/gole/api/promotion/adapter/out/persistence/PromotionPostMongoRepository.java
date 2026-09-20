package com.gole.api.promotion.adapter.out.persistence;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PromotionPostMongoRepository extends MongoRepository<PromotionPostDocument, String> {

    List<PromotionPostDocument> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    boolean existsByClaimedSourceCommitSha(String claimedSourceCommitSha);

    long countBySourceCommitSha(String sourceCommitSha);

    List<PromotionPostDocument> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
