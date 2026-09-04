package com.gole.api.chat.adapter.out.persistence;

import com.gole.api.chat.application.port.out.SupportAssistantAnalysisRepositoryPort;
import com.gole.api.chat.application.port.out.SupportAssistantPort.Analysis;
import com.gole.api.chat.application.port.out.SupportAssistantPort.Priority;
import com.gole.api.chat.domain.model.SupportCategory;
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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MongoSupportAssistantAnalysisAdapter implements SupportAssistantAnalysisRepositoryPort {

    private final SupportAssistantAnalysisMongoRepository analyses;
    private final MongoTemplate mongoTemplate;

    public MongoSupportAssistantAnalysisAdapter(
            SupportAssistantAnalysisMongoRepository analyses, MongoTemplate mongoTemplate) {
        this.analyses = analyses;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    @Transactional
    public boolean enqueue(String roomId, Instant requestedAt) {
        // 파기와 동일한 티켓 문서에 먼저 쓰기 펜스를 건다. 둘이 경합하면 Mongo 트랜잭션의
        // write conflict가 발생하고, 파기 완료 뒤에는 티켓이 없어 분석이 다시 생성되지 않는다.
        var fence = mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(roomId)),
                new Update().set("assistantWorkFenceAt", requestedAt),
                SupportTicketDocument.class);
        if (fence.getMatchedCount() == 0) {
            return false;
        }
        Query byRoom = Query.query(Criteria.where("_id").is(roomId));
        Update pending = new Update()
                .setOnInsert("state", SupportAssistantAnalysisDocument.PENDING)
                .setOnInsert("attempts", 0)
                .setOnInsert("requestedAt", requestedAt)
                .setOnInsert("nextAttemptAt", requestedAt)
                .setOnInsert("risk", List.of());
        return mongoTemplate
                        .upsert(byRoom, pending, SupportAssistantAnalysisDocument.class)
                        .getUpsertedId()
                != null;
    }

    @Override
    public Optional<Claim> tryClaim(String roomId, Instant startedAt, Instant leaseUntil, int maxAttempts) {
        String leaseToken = UUID.randomUUID().toString();
        Query claimable = claimableQuery(startedAt, maxAttempts);
        claimable.addCriteria(Criteria.where("_id").is(roomId));
        Update claim = new Update()
                .set("state", SupportAssistantAnalysisDocument.PROCESSING)
                .set("startedAt", startedAt)
                .set("leaseUntil", leaseUntil)
                .set("leaseToken", leaseToken)
                .unset("nextAttemptAt")
                .unset("completedAt")
                .inc("attempts", 1);
        SupportAssistantAnalysisDocument claimed = mongoTemplate.findAndModify(
                claimable,
                claim,
                FindAndModifyOptions.options().returnNew(true),
                SupportAssistantAnalysisDocument.class);
        if (claimed == null) {
            return Optional.empty();
        }
        return Optional.of(new Claim(roomId, leaseToken, claimed.getAttempts()));
    }

    @Override
    public void complete(String roomId, String leaseToken, Analysis analysis, Instant completedAt) {
        Query ownedLease = ownedLease(roomId, leaseToken);
        Update completed = new Update()
                .set("state", SupportAssistantAnalysisDocument.COMPLETED)
                .set("category", analysis.recommendedCategory().name())
                .set("priority", analysis.priority().name())
                .set("summary", analysis.summary())
                .set("draft", analysis.draftReply())
                .set("risk", analysis.riskFlags())
                .set("humanReview", analysis.humanReviewRequired())
                .set("externalModel", analysis.externalModelUsed())
                .set("engine", analysis.engineVersion())
                .set("completedAt", completedAt)
                .unset("leaseUntil")
                .unset("leaseToken")
                .unset("nextAttemptAt");
        mongoTemplate.updateFirst(ownedLease, completed, SupportAssistantAnalysisDocument.class);
    }

    @Override
    public void retry(String roomId, String leaseToken, Instant failedAt, Instant nextAttemptAt) {
        Update retry = new Update()
                .set("state", SupportAssistantAnalysisDocument.RETRY)
                .set("lastFailureAt", failedAt)
                .set("nextAttemptAt", nextAttemptAt)
                .unset("leaseUntil")
                .unset("leaseToken")
                .unset("completedAt");
        mongoTemplate.updateFirst(ownedLease(roomId, leaseToken), retry, SupportAssistantAnalysisDocument.class);
    }

    @Override
    public void fail(String roomId, String leaseToken, Instant completedAt) {
        Update failed = new Update()
                .set("state", SupportAssistantAnalysisDocument.FAILED)
                .set("lastFailureAt", completedAt)
                .set("completedAt", completedAt)
                .unset("leaseUntil")
                .unset("leaseToken")
                .unset("nextAttemptAt");
        mongoTemplate.updateFirst(ownedLease(roomId, leaseToken), failed, SupportAssistantAnalysisDocument.class);
    }

    @Override
    public List<String> findRecoverableRoomIds(Instant now, int maxAttempts, int limit) {
        Query query = claimableQuery(now, maxAttempts)
                .with(Sort.by(Sort.Order.asc("nextAttemptAt"), Sort.Order.asc("requestedAt")))
                .limit(Math.clamp(limit, 1, 100));
        return mongoTemplate.find(query, SupportAssistantAnalysisDocument.class).stream()
                .map(SupportAssistantAnalysisDocument::getRoomId)
                .toList();
    }

    @Override
    public Optional<StoredAnalysis> findCompletedByRoomId(String roomId) {
        return analyses.findById(roomId).flatMap(MongoSupportAssistantAnalysisAdapter::toCompleted);
    }

    @Override
    public List<StoredAnalysis> findCompletedByRoomIds(List<String> roomIds) {
        if (roomIds == null || roomIds.isEmpty()) {
            return List.of();
        }
        return analyses.findAllById(roomIds).stream()
                .map(MongoSupportAssistantAnalysisAdapter::toCompleted)
                .flatMap(Optional::stream)
                .toList();
    }

    private static Query claimableQuery(Instant now, int maxAttempts) {
        Criteria attemptsRemaining = new Criteria()
                .orOperator(
                        Criteria.where("attempts").exists(false),
                        Criteria.where("attempts").lt(maxAttempts));
        Criteria terminalizationAvailable = new Criteria()
                .orOperator(
                        Criteria.where("attempts").exists(false),
                        Criteria.where("attempts").lte(maxAttempts));
        Criteria dueOrMissing = new Criteria()
                .orOperator(
                        Criteria.where("nextAttemptAt").exists(false),
                        Criteria.where("nextAttemptAt").is(null),
                        Criteria.where("nextAttemptAt").lte(now));
        Criteria leaseExpiredOrMissing = new Criteria()
                .orOperator(
                        Criteria.where("leaseUntil").exists(false),
                        Criteria.where("leaseUntil").is(null),
                        Criteria.where("leaseUntil").lte(now));
        Criteria recoverableState = new Criteria()
                .orOperator(
                        new Criteria()
                                .andOperator(
                                        Criteria.where("state")
                                                .in(
                                                        SupportAssistantAnalysisDocument.PENDING,
                                                        SupportAssistantAnalysisDocument.RETRY,
                                                        SupportAssistantAnalysisDocument.FAILED),
                                        attemptsRemaining,
                                        dueOrMissing),
                        new Criteria()
                                .andOperator(
                                        Criteria.where("state").is(SupportAssistantAnalysisDocument.PROCESSING),
                                        terminalizationAvailable,
                                        leaseExpiredOrMissing));
        return Query.query(recoverableState);
    }

    private static Query ownedLease(String roomId, String leaseToken) {
        return Query.query(new Criteria()
                .andOperator(
                        Criteria.where("_id").is(roomId),
                        Criteria.where("state").is(SupportAssistantAnalysisDocument.PROCESSING),
                        Criteria.where("leaseToken").is(leaseToken)));
    }

    private static Optional<StoredAnalysis> toCompleted(SupportAssistantAnalysisDocument document) {
        if (!SupportAssistantAnalysisDocument.COMPLETED.equals(document.getState())
                || document.getCategory() == null
                || document.getPriority() == null
                || document.getSummary() == null
                || document.getDraft() == null
                || document.getHumanReview() == null
                || document.getExternalModel() == null
                || document.getEngine() == null
                || document.getCompletedAt() == null) {
            return Optional.empty();
        }
        try {
            Analysis analysis = new Analysis(
                    SupportCategory.valueOf(document.getCategory()),
                    Priority.valueOf(document.getPriority()),
                    document.getSummary(),
                    document.getDraft(),
                    document.getRisk(),
                    document.getHumanReview(),
                    document.getExternalModel(),
                    document.getEngine());
            return Optional.of(new StoredAnalysis(document.getRoomId(), analysis, document.getCompletedAt()));
        } catch (IllegalArgumentException invalidLegacyValue) {
            return Optional.empty();
        }
    }
}
