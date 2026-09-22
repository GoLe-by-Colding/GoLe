package com.gole.api.promotion.adapter.out.persistence;

import com.gole.api.promotion.application.port.out.PromotionDraftRequestRepositoryPort;
import com.gole.api.promotion.domain.model.PromotionDraftRequest;
import com.gole.api.promotion.domain.model.PromotionDraftRequestStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/** 홍보 초안 요청 영속 어댑터. 도메인 모델과 도큐먼트의 매핑은 여기 책임이다. */
@Repository
public class PromotionDraftRequestPersistenceAdapter implements PromotionDraftRequestRepositoryPort {

    /** 종료된 요청의 보존기간. 에이전트 세션 원장(RETENTION_DAYS)과 맞춘다. */
    private static final Duration TERMINAL_RETENTION = Duration.ofDays(7);

    private final MongoTemplate mongo;
    private final PromotionDraftRequestMongoRepository repository;

    public PromotionDraftRequestPersistenceAdapter(
            MongoTemplate mongo, PromotionDraftRequestMongoRepository repository) {
        this.mongo = mongo;
        this.repository = repository;
    }

    @Override
    public PromotionDraftRequest save(PromotionDraftRequest request) {
        return toDomain(mongo.save(toDocument(request)));
    }

    @Override
    public Optional<PromotionDraftRequest> findById(String requestId) {
        return repository.findById(requestId).map(PromotionDraftRequestPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<PromotionDraftRequest> claimNext(Instant now, Duration leaseDuration, int maximumAttempts) {
        // 마지막 허용 시도 중 에이전트가 죽은 요청은 영원히 IN_PROGRESS 로 남지 않게 닫는다.
        // 이걸 먼저 하지 않으면 아래 질의가 같은 문서를 계속 후보로 본다.
        mongo.updateMulti(
                Query.query(Criteria.where("status")
                        .is(PromotionDraftRequestStatus.IN_PROGRESS.name())
                        .and("leaseUntil")
                        .lte(now)
                        .and("attempts")
                        .gte(maximumAttempts)),
                new Update()
                        .set("status", PromotionDraftRequestStatus.FAILED.name())
                        .set("failureCode", "LEASE_EXPIRED_AFTER_MAX_ATTEMPTS")
                        .set("finishedAt", now)
                        .set("expiresAt", now.plus(TERMINAL_RETENTION))
                        .unset("leaseToken")
                        .unset("leaseUntil"),
                PromotionDraftRequestDocument.class);

        Criteria pending = Criteria.where("status")
                .is(PromotionDraftRequestStatus.PENDING.name())
                .and("attempts")
                .lt(maximumAttempts);
        Criteria abandoned = Criteria.where("status")
                .is(PromotionDraftRequestStatus.IN_PROGRESS.name())
                .and("leaseUntil")
                .lte(now)
                .and("attempts")
                .lt(maximumAttempts);
        Query due =
                Query.query(new Criteria().orOperator(pending, abandoned)).with(Sort.by(Sort.Order.asc("createdAt")));

        // 조회 후 갱신으로는 두 실행이 같은 요청을 집는 것을 막지 못한다 — 단일 findAndModify
        // 여야 한다(MongoSupportNotificationOutboxAdapter.claimNext 와 같은 이유).
        Update claim = new Update()
                .set("status", PromotionDraftRequestStatus.IN_PROGRESS.name())
                .set("leaseToken", UUID.randomUUID().toString())
                .set("leaseUntil", now.plus(leaseDuration))
                .inc("attempts", 1);
        PromotionDraftRequestDocument claimed = mongo.findAndModify(
                due, claim, FindAndModifyOptions.options().returnNew(true), PromotionDraftRequestDocument.class);
        return Optional.ofNullable(claimed).map(PromotionDraftRequestPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<PromotionDraftRequest> findActiveBySourceCommitSha(String sourceCommitSha) {
        return repository
                .findFirstBySourceCommitShaAndStatusInOrderByCreatedAtDesc(sourceCommitSha, activeStatuses())
                .map(PromotionDraftRequestPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<PromotionDraftRequest> findActiveWithoutSourceCommit() {
        return repository
                .findFirstBySourceCommitShaIsNullAndStatusInOrderByCreatedAtDesc(activeStatuses())
                .map(PromotionDraftRequestPersistenceAdapter::toDomain);
    }

    @Override
    public List<PromotionDraftRequest> findRecentFirst(int limit) {
        return repository.findByOrderByCreatedAtDesc(org.springframework.data.domain.PageRequest.of(0, limit)).stream()
                .map(PromotionDraftRequestPersistenceAdapter::toDomain)
                .toList();
    }

    private static List<String> activeStatuses() {
        return List.of(PromotionDraftRequestStatus.PENDING.name(), PromotionDraftRequestStatus.IN_PROGRESS.name());
    }

    private static PromotionDraftRequestDocument toDocument(PromotionDraftRequest request) {
        return new PromotionDraftRequestDocument(
                request.getId(),
                request.getSourceCommitSha(),
                request.getRequestedBy(),
                request.getStatus().name(),
                request.getAttempts(),
                request.getLeaseToken(),
                request.getLeaseUntil(),
                request.getPromotionPostId(),
                request.getFailureCode(),
                request.getCreatedAt(),
                request.getFinishedAt(),
                request.isTerminal() && request.getFinishedAt() != null
                        ? request.getFinishedAt().plus(TERMINAL_RETENTION)
                        : null);
    }

    private static PromotionDraftRequest toDomain(PromotionDraftRequestDocument document) {
        return new PromotionDraftRequest(
                document.getId(),
                document.getSourceCommitSha(),
                document.getRequestedBy(),
                document.getCreatedAt(),
                PromotionDraftRequestStatus.valueOf(document.getStatus()),
                document.getAttempts(),
                document.getLeaseToken(),
                document.getLeaseUntil(),
                document.getPromotionPostId(),
                document.getFailureCode(),
                document.getFinishedAt());
    }
}
