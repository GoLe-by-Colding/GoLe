package com.gole.api.promotion.adapter.out.persistence;

import com.gole.api.common.exception.ConflictException;
import com.gole.api.promotion.application.port.out.PromotionFeedbackRepositoryPort;
import com.gole.api.promotion.domain.model.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Component
public class PromotionFeedbackPersistenceAdapter implements PromotionFeedbackRepositoryPort {
    private final MongoTemplate mongo;

    public PromotionFeedbackPersistenceAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void insert(PromotionFeedback feedback) {
        mongo.insert(toDocument(feedback));
    }

    @Override
    public Optional<PromotionFeedback> findById(String id) {
        return Optional.ofNullable(mongo.findById(id, PromotionFeedbackDocument.class))
                .map(this::toDomain);
    }

    @Override
    public List<PromotionFeedback> findRecent(String postId, int limit) {
        Query query = new Query();
        if (postId != null) query.addCriteria(Criteria.where("postId").is(postId));
        return find(query.with(newest()).limit(limit));
    }

    @Override
    public List<PromotionFeedback> findRelevant(PromotionCategory category, List<String> routes, int limit) {
        List<PromotionFeedback> result = new ArrayList<>();
        if (!routes.isEmpty()) {
            result.addAll(find(Query.query(Criteria.where("category")
                            .is(category.name())
                            .and("snapshot.captures.route")
                            .in(routes))
                    .with(newest())
                    .limit(limit)));
        }
        if (result.size() < limit) {
            List<String> selected = result.stream().map(PromotionFeedback::id).toList();
            result.addAll(find(Query.query(Criteria.where("category")
                            .is(category.name())
                            .and("_id")
                            .nin(selected))
                    .with(newest())
                    .limit(limit - result.size())));
        }
        return List.copyOf(result);
    }

    @Override
    public List<PromotionFeedback> findUnreflected(int limit) {
        return find(Query.query(Criteria.where("reflectedAt").is(null))
                .with(Sort.by(Sort.Direction.ASC, "reviewedAt", "_id"))
                .limit(limit));
    }

    @Override
    public List<PromotionFeedback> findByReflectedRunKey(String runKey) {
        return find(Query.query(Criteria.where("reflectedRunKey").is(runKey)));
    }

    @Override
    public void markReflected(String id, String runKey, Instant now, boolean batchOwner) {
        Update update = new Update().set("reflectedAt", now).set("reflectedRunKey", runKey);
        // 묶음의 한 기록에만 unique 키를 둔다. 서로 다른 묶음의 같은 runKey 경합도 트랜잭션을 롤백한다.
        if (batchOwner) update.set("reflectionOwnerKey", runKey);
        long modified;
        try {
            modified = mongo.updateFirst(
                            Query.query(Criteria.where("_id")
                                    .is(id)
                                    .and("reflectedAt")
                                    .is(null)),
                            update,
                            PromotionFeedbackDocument.class)
                    .getModifiedCount();
        } catch (DuplicateKeyException conflict) {
            throw new ConflictException("PROMOTION_REFLECTION_BATCH_MISMATCH", "다른 반려 기록 묶음이 같은 실행 키를 사용했습니다.");
        }
        if (modified != 1) {
            throw new ConflictException("PROMOTION_FEEDBACK_ALREADY_REFLECTED", "이미 처리한 반려 기록입니다.");
        }
    }

    private List<PromotionFeedback> find(Query query) {
        return mongo.find(query, PromotionFeedbackDocument.class).stream()
                .map(this::toDomain)
                .toList();
    }

    private static Sort newest() {
        return Sort.by(Sort.Direction.DESC, "reviewedAt", "_id");
    }

    private PromotionFeedbackDocument toDocument(PromotionFeedback feedback) {
        var snapshot = feedback.snapshot();
        var provenance = snapshot.provenance();
        return new PromotionFeedbackDocument(
                feedback.id(),
                feedback.postId(),
                feedback.reviewerId(),
                feedback.reviewedAt(),
                feedback.reason(),
                feedback.category().name(),
                new PromotionFeedbackDocument.SnapshotDocument(
                        snapshot.caption(),
                        snapshot.mediaUrls(),
                        snapshot.captures().stream()
                                .map(capture -> new PromotionPostDocument.CaptureDocument(
                                        capture.label(),
                                        capture.route(),
                                        capture.actions(),
                                        capture.dataSource().name(),
                                        capture.capturedAt(),
                                        capture.originalUrl(),
                                        capture.edit()))
                                .toList(),
                        provenance == null
                                ? null
                                : new PromotionPostDocument.ProvenanceDocument(
                                        provenance.releaseTitle(), provenance.rationale(), provenance.runUrl()),
                        snapshot.sourceCommitSha()),
                feedback.reasonTags().stream().map(Enum::name).toList(),
                feedback.targets().stream().map(Enum::name).toList(),
                feedback.reflectedAt(),
                feedback.reflectedRunKey(),
                null);
    }

    private PromotionFeedback toDomain(PromotionFeedbackDocument document) {
        var snapshot = document.snapshot();
        var provenance = snapshot.provenance();
        return new PromotionFeedback(
                document.id(),
                document.postId(),
                document.reviewerId(),
                document.reviewedAt(),
                document.reason(),
                PromotionCategory.valueOf(document.category()),
                new PromotionFeedback.Snapshot(
                        snapshot.caption(),
                        snapshot.mediaUrls(),
                        snapshot.captures().stream()
                                .map(capture -> new PromotionCapture(
                                        capture.label(),
                                        capture.route(),
                                        capture.actions(),
                                        CaptureDataSource.valueOf(capture.dataSource()),
                                        capture.capturedAt(),
                                        capture.originalUrl(),
                                        capture.edit()))
                                .toList(),
                        provenance == null
                                ? null
                                : new PromotionProvenance(
                                        provenance.releaseTitle(), provenance.rationale(), provenance.runUrl()),
                        snapshot.sourceCommitSha()),
                document.reasonTags().stream().map(EvaluationReasonTag::valueOf).toList(),
                document.targets().stream().map(PromotionMemoryTarget::valueOf).toList(),
                document.reflectedAt(),
                document.reflectedRunKey());
    }
}
