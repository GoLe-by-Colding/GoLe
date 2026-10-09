package com.gole.api.promotion.adapter.out.persistence;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PromotionPostEvaluationMongoRepository
        extends MongoRepository<PromotionPostEvaluationDocument, String> {

    Optional<PromotionPostEvaluationDocument> findByPromotionPostId(String promotionPostId);
}
