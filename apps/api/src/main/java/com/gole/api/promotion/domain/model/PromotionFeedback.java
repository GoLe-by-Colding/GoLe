package com.gole.api.promotion.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 반려 당시의 경험. 재제출과 평가 수정으로 원문과 스냅샷이 바뀌지 않는다. */
public record PromotionFeedback(
        String id,
        String postId,
        String reviewerId,
        Instant reviewedAt,
        String reason,
        PromotionCategory category,
        Snapshot snapshot,
        List<EvaluationReasonTag> reasonTags,
        List<PromotionMemoryTarget> targets,
        Instant reflectedAt,
        String reflectedRunKey) {

    public PromotionFeedback {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(postId, "postId");
        Objects.requireNonNull(reviewerId, "reviewerId");
        Objects.requireNonNull(reviewedAt, "reviewedAt");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(snapshot, "snapshot");
        reasonTags = List.copyOf(reasonTags);
        targets = List.copyOf(targets);
    }

    public static PromotionFeedback rejected(String id, PromotionPost post, List<EvaluationReasonTag> tags) {
        return new PromotionFeedback(
                id,
                post.getId(),
                post.getReviewerId(),
                post.getReviewedAt(),
                post.getRejectionReason(),
                post.getCategory(),
                new Snapshot(
                        post.getCaption(),
                        post.getMediaUrls(),
                        post.getCaptures(),
                        post.getProvenance(),
                        post.getSourceCommitSha()),
                tags,
                List.of(PromotionMemoryTarget.values()),
                null,
                null);
    }

    public record Snapshot(
            String caption,
            List<String> mediaUrls,
            List<PromotionCapture> captures,
            PromotionProvenance provenance,
            String sourceCommitSha) {
        public Snapshot {
            Objects.requireNonNull(caption, "caption");
            mediaUrls = List.copyOf(mediaUrls);
            captures = List.copyOf(captures);
        }
    }
}
