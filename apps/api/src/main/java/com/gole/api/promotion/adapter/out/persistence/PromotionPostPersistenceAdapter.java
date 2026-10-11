package com.gole.api.promotion.adapter.out.persistence;

import com.gole.api.promotion.application.port.out.PromotionPostRepositoryPort;
import com.gole.api.promotion.application.port.out.PromotionPostRepositoryPort.ReviewTimestamps;
import com.gole.api.promotion.domain.exception.PromotionPublishTooSoonException;
import com.gole.api.promotion.domain.exception.SourceCommitAlreadyPromotedException;
import com.gole.api.promotion.domain.model.CaptureDataSource;
import com.gole.api.promotion.domain.model.PromotionCapture;
import com.gole.api.promotion.domain.model.PromotionCategory;
import com.gole.api.promotion.domain.model.PromotionChannel;
import com.gole.api.promotion.domain.model.PromotionPost;
import com.gole.api.promotion.domain.model.PromotionPostContext;
import com.gole.api.promotion.domain.model.PromotionPostStatus;
import com.gole.api.promotion.domain.model.PromotionProvenance;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * 홍보 게시물 영속성 어댑터. 도메인 {@link PromotionPost}와 {@link PromotionPostDocument}를
 * 양방향 매핑한다.
 */
@Component
public class PromotionPostPersistenceAdapter implements PromotionPostRepositoryPort {

    /** 발행 채널이 Threads 하나라 슬롯도 하나다. */
    private static final String PUBLISH_SLOT_ID = "THREADS";

    private final PromotionPostMongoRepository repository;
    private final MongoTemplate mongo;

    public PromotionPostPersistenceAdapter(PromotionPostMongoRepository repository, MongoTemplate mongo) {
        this.repository = repository;
        this.mongo = mongo;
    }

    @Override
    public PromotionPost save(PromotionPost promotionPost) {
        try {
            return toDomain(repository.save(toDocument(promotionPost)));
        } catch (DuplicateKeyException conflict) {
            if (promotionPost.getClaimedSourceCommitSha() != null) {
                throw new SourceCommitAlreadyPromotedException(promotionPost.getClaimedSourceCommitSha());
            }
            throw conflict;
        }
    }

    @Override
    public Optional<PromotionPost> findById(String promotionPostId) {
        return repository.findById(promotionPostId).map(this::toDomain);
    }

    @Override
    public boolean existsBySourceCommitSha(String sourceCommitSha) {
        // 출처가 아니라 점유를 본다 — 반려된 초안은 점유를 놓아줬으므로 걸리지 않는다(D2).
        return repository.existsByClaimedSourceCommitSha(sourceCommitSha);
    }

    @Override
    public long countBySourceCommitSha(String sourceCommitSha) {
        // 이쪽은 출처다 — 반려된 것까지 세야 재시도가 몇 번째인지 알 수 있다.
        return repository.countBySourceCommitSha(sourceCommitSha);
    }

    @Override
    public List<PromotionPost> findRecentFirst(PromotionPostStatus status, int limit) {
        PageRequest page = PageRequest.of(0, Math.max(1, limit));
        List<PromotionPostDocument> documents;
        if (status == null) {
            documents = repository.findAllByOrderByCreatedAtDesc(page);
        } else if (status == PromotionPostStatus.PUBLISHED) {
            // 생성 순으로 자르면 오래전에 만든 글을 방금 발행했을 때 그 글이 목록 밖으로 밀려
            // 발행 에이전트의 최소 간격 가드가 최신 발행을 보지 못한다.
            documents = repository.findByStatusOrderByPublishedAtDesc(status.name(), page);
        } else {
            documents = repository.findByStatusOrderByCreatedAtDesc(status.name(), page);
        }
        return documents.stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<PromotionPost> findOldestApproved() {
        return repository
                .findFirstByStatusOrderByReviewedAtAsc(PromotionPostStatus.APPROVED.name())
                .map(this::toDomain);
    }

    @Override
    public Instant claimPublishSlot(Instant now, Duration interval) {
        // 슬롯이 없으면 기존 발행 이력으로 시작한다 — 배포 직후 첫 발행도 직전 발행 기준으로 판정된다.
        mongo.upsert(
                slot(),
                new Update().setOnInsert("lastPublishedAt", latestPublishedAt().orElse(Instant.EPOCH)),
                PublishSlotDocument.class);
        PublishSlotDocument previous = mongo.findAndModify(
                Query.query(Criteria.where("_id")
                        .is(PUBLISH_SLOT_ID)
                        .and("lastPublishedAt")
                        .lte(now.minus(interval))),
                new Update().set("lastPublishedAt", millis(now)),
                FindAndModifyOptions.options().returnNew(false),
                PublishSlotDocument.class);
        if (previous != null) {
            return previous.lastPublishedAt();
        }
        PublishSlotDocument current = mongo.findOne(slot(), PublishSlotDocument.class);
        Instant last = current == null ? now : current.lastPublishedAt();
        throw new PromotionPublishTooSoonException(last.plus(interval));
    }

    @Override
    public void releasePublishSlot(Instant claimedAt, Instant previous) {
        mongo.updateFirst(
                Query.query(Criteria.where("_id")
                        .is(PUBLISH_SLOT_ID)
                        .and("lastPublishedAt")
                        .is(millis(claimedAt))),
                new Update().set("lastPublishedAt", previous),
                PublishSlotDocument.class);
    }

    private Optional<Instant> latestPublishedAt() {
        return repository
                .findFirstByStatusOrderByPublishedAtDesc(PromotionPostStatus.PUBLISHED.name())
                .map(PromotionPostDocument::getPublishedAt);
    }

    /** Mongo 는 밀리초까지만 저장한다 — 맞추지 않으면 반납의 동등 비교가 절대 맞지 않는다. */
    private static Instant millis(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MILLIS);
    }

    private static Query slot() {
        return Query.query(Criteria.where("_id").is(PUBLISH_SLOT_ID));
    }

    /** 발행 간격의 경합 지점. 문서 하나라 findAndModify 하나로 원자적이다(D24). */
    @Document("promotion_publish_slot")
    record PublishSlotDocument(@Id String id, Instant lastPublishedAt) {}

    @Override
    public long countByStatus(PromotionPostStatus status) {
        return repository.countByStatus(status.name());
    }

    @Override
    public List<ReviewTimestamps> findReviewTimestamps() {
        // 필터가 쿼리에 있으므로 여기서 다시 null 을 거르지 않는다.
        return repository.findBySubmittedAtNotNullAndReviewedAtNotNull().stream()
                .map(projection -> new ReviewTimestamps(projection.getSubmittedAt(), projection.getReviewedAt()))
                .toList();
    }

    private PromotionPostDocument toDocument(PromotionPost promotionPost) {
        PromotionPostDocument document = new PromotionPostDocument(
                promotionPost.getId(),
                promotionPost.getChannel().name(),
                promotionPost.getCaption(),
                promotionPost.getMediaUrls(),
                promotionPost.getAuthorId(),
                promotionPost.getSourceCommitSha(),
                promotionPost.getClaimedSourceCommitSha(),
                promotionPost.getStatus().name(),
                promotionPost.getCreatedAt(),
                promotionPost.getSubmittedAt(),
                promotionPost.getReviewerId(),
                promotionPost.getReviewedAt(),
                promotionPost.getRejectionReason(),
                promotionPost.getPublishedAt(),
                promotionPost.getExternalPostId());
        PromotionPostContext context = promotionPost.context();
        PromotionProvenance provenance = context.provenance();
        document.setContext(
                context.category().name(),
                context.captures().stream()
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
                                provenance.releaseTitle(), provenance.rationale(), provenance.runUrl()));
        return document;
    }

    private static PromotionPostContext toContext(PromotionPostDocument document) {
        List<PromotionCapture> captures = document.getCaptures() == null
                ? List.of()
                : document.getCaptures().stream()
                        .map(capture -> new PromotionCapture(
                                capture.label(),
                                capture.route(),
                                capture.actions(),
                                CaptureDataSource.valueOf(capture.dataSource()),
                                capture.capturedAt(),
                                capture.originalUrl(),
                                capture.edit()))
                        .toList();
        PromotionPostDocument.ProvenanceDocument provenance = document.getProvenance();
        return new PromotionPostContext(
                document.getCategory() == null ? null : PromotionCategory.valueOf(document.getCategory()),
                captures,
                provenance == null
                        ? null
                        : new PromotionProvenance(
                                provenance.releaseTitle(), provenance.rationale(), provenance.runUrl()));
    }

    private PromotionPost toDomain(PromotionPostDocument document) {
        return new PromotionPost(
                document.getId(),
                PromotionChannel.valueOf(document.getChannel()),
                document.getCaption(),
                document.getMediaUrls(),
                document.getAuthorId(),
                document.getSourceCommitSha(),
                document.getClaimedSourceCommitSha(),
                PromotionPostStatus.valueOf(document.getStatus()),
                document.getCreatedAt(),
                document.getSubmittedAt(),
                document.getReviewerId(),
                document.getReviewedAt(),
                document.getRejectionReason(),
                document.getPublishedAt(),
                document.getExternalPostId(),
                toContext(document));
    }
}
