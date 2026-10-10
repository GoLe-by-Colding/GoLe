package com.gole.api.promotion.application.port.out;

import com.gole.api.promotion.domain.model.PromotionCategory;
import com.gole.api.promotion.domain.model.PromotionFeedback;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PromotionFeedbackRepositoryPort {
    void insert(PromotionFeedback feedback);

    Optional<PromotionFeedback> findById(String id);

    List<PromotionFeedback> findRecent(String postId, int limit);

    List<PromotionFeedback> findRelevant(PromotionCategory category, List<String> routes, int limit);

    List<PromotionFeedback> findUnreflected(int limit);

    List<PromotionFeedback> findByReflectedRunKey(String runKey);

    void markReflected(String id, String runKey, Instant now, boolean batchOwner);
}
