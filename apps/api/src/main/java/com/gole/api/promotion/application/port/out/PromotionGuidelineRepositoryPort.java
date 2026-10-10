package com.gole.api.promotion.application.port.out;

import com.gole.api.promotion.domain.model.PromotionCategory;
import com.gole.api.promotion.domain.model.PromotionGuideline;
import com.gole.api.promotion.domain.model.PromotionGuidelineStatus;
import java.util.List;
import java.util.Optional;

public interface PromotionGuidelineRepositoryPort {
    void insert(PromotionGuideline guideline);

    PromotionGuideline save(PromotionGuideline guideline);

    Optional<PromotionGuideline> findById(String id);

    List<PromotionGuideline> findRecent(PromotionGuidelineStatus status, int limit);

    List<PromotionGuideline> findActive(PromotionCategory category, int limit);

    List<PromotionGuideline> findByReflectionRunKey(String runKey);
}
