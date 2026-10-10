package com.gole.api.promotion.domain.model;

import java.util.List;
import java.util.Objects;

/** 실행 당시 실제로 전달한 기억의 추적 정보. 지침 변경 후에도 내용을 재현한다. */
public record PromotionMemoryContext(List<String> feedbackIds, List<GuidelineSnapshot> guidelines) {
    public static final PromotionMemoryContext EMPTY = new PromotionMemoryContext(List.of(), List.of());

    public PromotionMemoryContext {
        feedbackIds = feedbackIds == null ? List.of() : List.copyOf(feedbackIds);
        guidelines = guidelines == null ? List.of() : List.copyOf(guidelines);
        if (feedbackIds.size() > 3
                || guidelines.size() > 8
                || feedbackIds.stream().anyMatch(id -> id.isBlank() || id.length() > 80)
                || feedbackIds.stream().distinct().count() != feedbackIds.size()
                || guidelines.stream().map(GuidelineSnapshot::id).distinct().count() != guidelines.size()) {
            throw new IllegalArgumentException("memoryContext exceeds its budget or repeats ids");
        }
    }

    public record GuidelineSnapshot(
            String id,
            PromotionGuidelineKind kind,
            String content,
            List<PromotionMemoryTarget> targets,
            List<PromotionCategory> categories) {
        public GuidelineSnapshot {
            if (id == null || id.isBlank() || id.length() > 80) {
                throw new IllegalArgumentException("guideline id must be 1..80 characters");
            }
            Objects.requireNonNull(kind, "kind");
            if (content == null || content.isBlank() || content.length() > PromotionGuideline.MAX_CONTENT) {
                throw new IllegalArgumentException("invalid guideline content");
            }
            targets = List.copyOf(targets);
            categories = List.copyOf(categories);
            if (targets.isEmpty()
                    || categories.isEmpty()
                    || targets.size() > 3
                    || categories.size() > 2
                    || targets.stream().distinct().count() != targets.size()
                    || categories.stream().distinct().count() != categories.size()) {
                throw new IllegalArgumentException("invalid guideline scope");
            }
        }
    }
}
