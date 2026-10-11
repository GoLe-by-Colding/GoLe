package com.gole.api.promotion.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "promotion_guidelines")
public record PromotionGuidelineDocument(
        @Id String id,
        String kind,
        String content,
        List<String> targets,
        @Indexed List<String> categories,
        List<String> sourceFeedbackIds,
        @Indexed String status,
        String proposedBy,
        Instant createdAt,
        @Indexed Instant updatedAt,
        String confirmedBy,
        Instant confirmedAt,
        @Indexed String reflectionRunKey,
        Long version) {}
