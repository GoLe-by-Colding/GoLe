package com.gole.api.promotion.adapter.out.persistence;

import com.gole.api.common.exception.ConflictException;
import com.gole.api.promotion.application.port.out.PromotionGuidelineRepositoryPort;
import com.gole.api.promotion.domain.model.*;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Component
public class PromotionGuidelinePersistenceAdapter implements PromotionGuidelineRepositoryPort {
    private final MongoTemplate mongo;

    public PromotionGuidelinePersistenceAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void insert(PromotionGuideline guideline) {
        mongo.insert(toDocument(guideline));
    }

    @Override
    public PromotionGuideline saveIfVersion(PromotionGuideline guideline, long expectedVersion) {
        Criteria version = expectedVersion == 0
                ? new Criteria()
                        .orOperator(
                                Criteria.where("version").is(0L),
                                Criteria.where("version").is(null))
                : Criteria.where("version").is(expectedVersion);
        Query query =
                Query.query(new Criteria().andOperator(Criteria.where("_id").is(guideline.id()), version));
        Update update = new Update()
                .set("content", guideline.content())
                .set("targets", guideline.targets().stream().map(Enum::name).toList())
                .set(
                        "categories",
                        guideline.categories().stream().map(Enum::name).toList())
                .set("status", guideline.status().name())
                .set("updatedAt", guideline.updatedAt())
                .set("confirmedBy", guideline.confirmedBy())
                .set("confirmedAt", guideline.confirmedAt())
                .set("version", guideline.version());
        PromotionGuidelineDocument saved = mongo.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), PromotionGuidelineDocument.class);
        if (saved == null) {
            throw new ConflictException("PROMOTION_GUIDELINE_VERSION_CONFLICT", "다른 관리자가 변경했습니다. 최신 내용을 확인해주세요.");
        }
        return toDomain(saved);
    }

    @Override
    public Optional<PromotionGuideline> findById(String id) {
        return Optional.ofNullable(mongo.findById(id, PromotionGuidelineDocument.class))
                .map(this::toDomain);
    }

    @Override
    public List<PromotionGuideline> findRecent(PromotionGuidelineStatus status, int limit) {
        Query query = new Query();
        if (status != null) query.addCriteria(Criteria.where("status").is(status.name()));
        return find(query.limit(limit).with(newest()));
    }

    @Override
    public List<PromotionGuideline> findActive(PromotionCategory category, int limit) {
        return find(Query.query(Criteria.where("status")
                        .is(PromotionGuidelineStatus.ACTIVE.name())
                        .and("categories")
                        .is(category.name()))
                .with(newest())
                .limit(limit));
    }

    @Override
    public List<PromotionGuideline> findByReflectionRunKey(String runKey) {
        return find(Query.query(Criteria.where("reflectionRunKey").is(runKey)).with(Sort.by("_id")));
    }

    private List<PromotionGuideline> find(Query query) {
        return mongo.find(query, PromotionGuidelineDocument.class).stream()
                .map(this::toDomain)
                .toList();
    }

    private static Sort newest() {
        return Sort.by(Sort.Direction.DESC, "updatedAt", "_id");
    }

    private PromotionGuidelineDocument toDocument(PromotionGuideline guideline) {
        return new PromotionGuidelineDocument(
                guideline.id(),
                guideline.kind().name(),
                guideline.content(),
                guideline.targets().stream().map(Enum::name).toList(),
                guideline.categories().stream().map(Enum::name).toList(),
                guideline.sourceFeedbackIds(),
                guideline.status().name(),
                guideline.proposedBy(),
                guideline.createdAt(),
                guideline.updatedAt(),
                guideline.confirmedBy(),
                guideline.confirmedAt(),
                guideline.reflectionRunKey(),
                guideline.version());
    }

    private PromotionGuideline toDomain(PromotionGuidelineDocument document) {
        return new PromotionGuideline(
                document.id(),
                PromotionGuidelineKind.valueOf(document.kind()),
                document.content(),
                document.targets().stream().map(PromotionMemoryTarget::valueOf).toList(),
                document.categories().stream().map(PromotionCategory::valueOf).toList(),
                document.sourceFeedbackIds(),
                PromotionGuidelineStatus.valueOf(document.status()),
                document.proposedBy(),
                document.createdAt(),
                document.updatedAt(),
                document.confirmedBy(),
                document.confirmedAt(),
                document.reflectionRunKey(),
                document.version() == null ? 0 : document.version());
    }
}
