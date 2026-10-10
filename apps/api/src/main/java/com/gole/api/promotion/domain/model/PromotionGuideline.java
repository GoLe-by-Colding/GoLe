package com.gole.api.promotion.domain.model;

import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.exception.ForbiddenException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 모델이 제안하고 사람이 확정하는 지침. 제안자의 출처는 수정해도 보존한다. */
public record PromotionGuideline(
        String id,
        PromotionGuidelineKind kind,
        String content,
        List<PromotionMemoryTarget> targets,
        List<PromotionCategory> categories,
        List<String> sourceFeedbackIds,
        PromotionGuidelineStatus status,
        String proposedBy,
        Instant createdAt,
        Instant updatedAt,
        String confirmedBy,
        Instant confirmedAt,
        String reflectionRunKey,
        long version) {

    public static final int MAX_CONTENT = 1000;

    public PromotionGuideline {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        if (content == null || content.isBlank() || content.strip().length() > MAX_CONTENT) {
            throw new IllegalArgumentException("content must be 1..1000 characters");
        }
        content = content.strip();
        targets = List.copyOf(targets);
        categories = List.copyOf(categories);
        sourceFeedbackIds = List.copyOf(sourceFeedbackIds);
        if (targets.isEmpty()
                || categories.isEmpty()
                || sourceFeedbackIds.isEmpty()
                || targets.size() > PromotionMemoryTarget.values().length
                || categories.size() > PromotionCategory.values().length
                || sourceFeedbackIds.size() > 3
                || targets.stream().distinct().count() != targets.size()
                || categories.stream().distinct().count() != categories.size()
                || sourceFeedbackIds.stream().anyMatch(value -> value.isBlank() || value.length() > 80)
                || sourceFeedbackIds.stream().distinct().count() != sourceFeedbackIds.size()) {
            throw new IllegalArgumentException("guideline scope and feedback references must be nonempty and unique");
        }
        Objects.requireNonNull(status, "status");
        if (proposedBy == null || proposedBy.isBlank()) {
            throw new IllegalArgumentException("proposedBy must not be blank");
        }
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(reflectionRunKey, "reflectionRunKey");
        if (version < 0) throw new IllegalArgumentException("version must be nonnegative");
    }

    public PromotionGuideline edit(
            String content, List<PromotionMemoryTarget> targets, List<PromotionCategory> categories, Instant now) {
        requireStatus(PromotionGuidelineStatus.PROPOSED);
        return new PromotionGuideline(
                id,
                kind,
                content,
                targets,
                categories,
                sourceFeedbackIds,
                status,
                proposedBy,
                createdAt,
                now,
                confirmedBy,
                confirmedAt,
                reflectionRunKey,
                Math.incrementExact(version));
    }

    public PromotionGuideline activate(String actorId, Instant now) {
        if (proposedBy.equals(actorId)) {
            throw new ForbiddenException("PROMOTION_GUIDELINE_SELF_CONFIRM", "자신이 제안한 지침은 확정할 수 없습니다.");
        }
        if (status == PromotionGuidelineStatus.ACTIVE) return this;
        requireStatus(PromotionGuidelineStatus.PROPOSED);
        return new PromotionGuideline(
                id,
                kind,
                content,
                targets,
                categories,
                sourceFeedbackIds,
                PromotionGuidelineStatus.ACTIVE,
                proposedBy,
                createdAt,
                now,
                actorId,
                now,
                reflectionRunKey,
                Math.incrementExact(version));
    }

    public PromotionGuideline dismiss(Instant now) {
        return move(PromotionGuidelineStatus.PROPOSED, PromotionGuidelineStatus.DISMISSED, now);
    }

    public PromotionGuideline retire(Instant now) {
        return move(PromotionGuidelineStatus.ACTIVE, PromotionGuidelineStatus.RETIRED, now);
    }

    private PromotionGuideline move(PromotionGuidelineStatus expected, PromotionGuidelineStatus next, Instant now) {
        if (status == next) return this;
        requireStatus(expected);
        return new PromotionGuideline(
                id,
                kind,
                content,
                targets,
                categories,
                sourceFeedbackIds,
                next,
                proposedBy,
                createdAt,
                now,
                confirmedBy,
                confirmedAt,
                reflectionRunKey,
                Math.incrementExact(version));
    }

    private void requireStatus(PromotionGuidelineStatus expected) {
        if (status != expected) {
            throw new ConflictException("INVALID_PROMOTION_GUIDELINE_STATE", "이 상태에서는 지침을 변경할 수 없습니다.");
        }
    }
}
