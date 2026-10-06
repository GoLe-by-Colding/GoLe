package com.gole.api.promotion.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PromotionRunMongoRepository extends MongoRepository<PromotionRunDocument, String> {

    Optional<PromotionRunDocument> findByRunKey(String runKey);

    List<PromotionRunDocument> findAllByOrderByRecordedAtDesc(Pageable pageable);
}
