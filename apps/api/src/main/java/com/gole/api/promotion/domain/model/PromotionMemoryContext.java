package com.gole.api.promotion.domain.model;

import java.util.List;
import java.util.Objects;

/** 실행 당시 실제로 전달한 기억의 추적 정보. 지침 변경 후에도 내용을 재현한다. */
public record PromotionMemoryContext(
        List<String> feedbackIds, List<GuidelineSnapshot> guidelines, List<StageSnapshot> stages) {
    public static final PromotionMemoryContext EMPTY = new PromotionMemoryContext(List.of(), List.of());

    public PromotionMemoryContext(List<String> feedbackIds, List<GuidelineSnapshot> guidelines) {
        this(feedbackIds, guidelines, List.of());
    }

    public PromotionMemoryContext {
        feedbackIds = feedbackIds == null ? List.of() : List.copyOf(feedbackIds);
        guidelines = guidelines == null ? List.of() : List.copyOf(guidelines);
        stages = stages == null ? List.of() : List.copyOf(stages);
        if (feedbackIds.size() > 6
                || feedbackIds.stream().anyMatch(id -> id.isBlank() || id.length() > 80)
                || feedbackIds.stream().distinct().count() != feedbackIds.size()
                || guidelines.stream().map(GuidelineSnapshot::id).distinct().count() != guidelines.size()) {
            throw new IllegalArgumentException("memoryContext exceeds its budget or repeats ids");
        }
        var guidelineIds = guidelines.stream().map(GuidelineSnapshot::id).toList();
        var recordedFeedback = feedbackIds;
        if (stages.size() > Stage.values().length
                || stages.stream().map(StageSnapshot::stage).distinct().count() != stages.size()
                || stages.stream()
                        .anyMatch(stage -> !recordedFeedback.containsAll(stage.feedbackIds())
                                || !guidelineIds.containsAll(stage.guidelineIds()))) {
            throw new IllegalArgumentException("stage references must identify the recorded memory");
        }
    }

    public enum Stage {
        PLAN,
        IMAGE_EDIT
    }

    public record StageSnapshot(Stage stage, List<String> routes, List<String> feedbackIds, List<String> guidelineIds) {
        public StageSnapshot {
            Objects.requireNonNull(stage, "stage");
            routes = List.copyOf(routes);
            feedbackIds = List.copyOf(feedbackIds);
            guidelineIds = List.copyOf(guidelineIds);
            if (routes.size() > 30
                    || routes.stream().anyMatch(route -> !route.startsWith("/") || route.length() > 300)
                    || routes.stream().distinct().count() != routes.size()
                    || feedbackIds.size() > 3
                    || !validIds(feedbackIds)
                    || !validIds(guidelineIds)) {
                throw new IllegalArgumentException("invalid stage memory");
            }
        }

        private static boolean validIds(List<String> ids) {
            return ids.stream().noneMatch(id -> id.isBlank() || id.length() > 80)
                    && ids.stream().distinct().count() == ids.size();
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
