package com.gole.api.promotion.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** 경험 원문/스냅샷은 불변이며 성찰 처리 표시만 갱신한다. */
@Document(collection = "promotion_feedback")
public record PromotionFeedbackDocument(
        @Id String id,
        @Indexed String postId,
        String reviewerId,
        @Indexed Instant reviewedAt,
        String reason,
        @Indexed String category,
        SnapshotDocument snapshot,
        List<String> reasonTags,
        List<String> targets,
        @Indexed Instant reflectedAt,
        @Indexed String reflectedRunKey,
        @Indexed(unique = true, sparse = true) String reflectionOwnerKey) {

    public record SnapshotDocument(
            String caption,
            List<String> mediaUrls,
            List<PromotionPostDocument.CaptureDocument> captures,
            PromotionPostDocument.ProvenanceDocument provenance,
            String sourceCommitSha) {}
}
