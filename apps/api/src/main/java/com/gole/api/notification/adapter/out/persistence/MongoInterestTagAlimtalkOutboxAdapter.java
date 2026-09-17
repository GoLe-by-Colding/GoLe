package com.gole.api.notification.adapter.out.persistence;

import com.gole.api.notification.application.port.out.InterestTagAlimtalkOutboxPort;
import com.gole.api.notification.application.service.InterestTagAlimtalkProperties;
import com.gole.api.notification.domain.model.InterestTagAlimtalkEvent;
import com.gole.api.notification.domain.model.InterestTagAlimtalkEvent.State;
import com.gole.api.notification.domain.model.InterestTagAlimtalkEvent.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/** 결정적 ID와 원자 lease를 사용하는 Mongo 관심태그 알림톡 아웃박스. */
@Component
public class MongoInterestTagAlimtalkOutboxAdapter implements InterestTagAlimtalkOutboxPort {

    private final MongoTemplate mongo;
    private final InterestTagAlimtalkProperties properties;

    public MongoInterestTagAlimtalkOutboxAdapter(MongoTemplate mongo, InterestTagAlimtalkProperties properties) {
        this.mongo = mongo;
        this.properties = properties;
    }

    @Override
    public void enqueue(InterestTagAlimtalkEvent event) {
        try {
            mongo.insert(toDocument(event));
        } catch (DuplicateKeyException ignored) {
            // 결정적 eventId를 이미 적재했다면 같은 팬아웃 페이지 재처리는 성공으로 본다.
        }
    }

    @Override
    public Optional<InterestTagAlimtalkEvent> claimNext(Instant now, Duration leaseDuration, int maximumAttempts) {
        mongo.updateMulti(
                Query.query(Criteria.where("state")
                        .is(State.IN_FLIGHT.name())
                        .and("leaseUntil")
                        .lte(now)
                        .and("attempts")
                        .gte(maximumAttempts)),
                new Update()
                        .set("state", State.DEAD_LETTER.name())
                        .set("lastErrorCode", "LEASE_EXPIRED_AFTER_MAX_ATTEMPTS")
                        .set("completedAt", now)
                        .set("expiresAt", now.plus(properties.terminalRetention()))
                        .unset("nextAttemptAt")
                        .unset("leaseToken")
                        .unset("leaseUntil"),
                InterestTagAlimtalkOutboxDocument.class);

        Criteria pending = Criteria.where("state")
                .is(State.PENDING.name())
                .and("nextAttemptAt")
                .lte(now)
                .and("attempts")
                .lt(maximumAttempts);
        Criteria abandoned = Criteria.where("state")
                .is(State.IN_FLIGHT.name())
                .and("leaseUntil")
                .lte(now)
                .and("attempts")
                .lt(maximumAttempts);
        Query due = Query.query(new Criteria().orOperator(pending, abandoned))
                .with(Sort.by(Sort.Order.asc("nextAttemptAt"), Sort.Order.asc("createdAt")));
        String leaseToken = UUID.randomUUID().toString();
        Update claim = new Update()
                .set("state", State.IN_FLIGHT.name())
                .set("leaseToken", leaseToken)
                .set("leaseUntil", now.plus(leaseDuration))
                .inc("attempts", 1);
        InterestTagAlimtalkOutboxDocument claimed = mongo.findAndModify(
                due, claim, FindAndModifyOptions.options().returnNew(true), InterestTagAlimtalkOutboxDocument.class);
        return Optional.ofNullable(claimed).map(MongoInterestTagAlimtalkOutboxAdapter::toDomain);
    }

    @Override
    public void delivered(String eventId, String leaseToken, Instant deliveredAt) {
        mongo.updateFirst(
                ownedLease(eventId, leaseToken),
                new Update()
                        .set("state", State.DELIVERED.name())
                        .set("completedAt", deliveredAt)
                        .set("expiresAt", deliveredAt.plus(properties.terminalRetention()))
                        .unset("nextAttemptAt")
                        .unset("leaseToken")
                        .unset("leaseUntil")
                        .unset("lastErrorCode"),
                InterestTagAlimtalkOutboxDocument.class);
    }

    @Override
    public void continueFanout(
            String eventId, String leaseToken, String cursorAccountId, long enqueuedRecipients, Instant nextAttemptAt) {
        mongo.updateFirst(
                ownedLease(eventId, leaseToken),
                new Update()
                        .set("state", State.PENDING.name())
                        .set("cursorAccountId", cursorAccountId)
                        .set("enqueuedRecipients", enqueuedRecipients)
                        .set("nextAttemptAt", nextAttemptAt)
                        .inc("attempts", -1)
                        .unset("leaseToken")
                        .unset("leaseUntil")
                        .unset("lastErrorCode")
                        .unset("completedAt")
                        .unset("expiresAt"),
                InterestTagAlimtalkOutboxDocument.class);
    }

    @Override
    public void skipped(String eventId, String leaseToken, String reasonCode, Instant skippedAt) {
        mongo.updateFirst(
                ownedLease(eventId, leaseToken),
                new Update()
                        .set("state", State.SKIPPED.name())
                        .set("lastErrorCode", reasonCode)
                        .set("completedAt", skippedAt)
                        .set("expiresAt", skippedAt.plus(properties.terminalRetention()))
                        .unset("nextAttemptAt")
                        .unset("leaseToken")
                        .unset("leaseUntil"),
                InterestTagAlimtalkOutboxDocument.class);
    }

    @Override
    public void retry(
            String eventId,
            String leaseToken,
            Instant failedAt,
            Instant nextAttemptAt,
            String errorCode,
            boolean deadLetter) {
        Update update = new Update()
                .set("state", (deadLetter ? State.DEAD_LETTER : State.PENDING).name())
                .set("lastErrorCode", errorCode)
                .unset("leaseToken")
                .unset("leaseUntil");
        if (deadLetter) {
            update.unset("nextAttemptAt");
            update.set("completedAt", failedAt);
            update.set("expiresAt", failedAt.plus(properties.terminalRetention()));
        } else {
            update.set("nextAttemptAt", nextAttemptAt);
            update.unset("completedAt");
            update.unset("expiresAt");
        }
        mongo.updateFirst(ownedLease(eventId, leaseToken), update, InterestTagAlimtalkOutboxDocument.class);
    }

    @Override
    public Optional<InterestTagAlimtalkEvent> requeueDeadLetter(String eventId, Instant requeuedAt) {
        Query deadLetter =
                Query.query(Criteria.where("_id").is(eventId).and("state").is(State.DEAD_LETTER.name()));
        Update requeue = new Update()
                .set("state", State.PENDING.name())
                .set("attempts", 0)
                .set("nextAttemptAt", requeuedAt)
                .unset("leaseToken")
                .unset("leaseUntil")
                .unset("lastErrorCode")
                .unset("completedAt")
                .unset("expiresAt");
        InterestTagAlimtalkOutboxDocument updated = mongo.findAndModify(
                deadLetter,
                requeue,
                FindAndModifyOptions.options().returnNew(true),
                InterestTagAlimtalkOutboxDocument.class);
        return Optional.ofNullable(updated).map(MongoInterestTagAlimtalkOutboxAdapter::toDomain);
    }

    @Override
    public Optional<InterestTagAlimtalkEvent> findById(String eventId) {
        return Optional.ofNullable(mongo.findById(eventId, InterestTagAlimtalkOutboxDocument.class))
                .map(MongoInterestTagAlimtalkOutboxAdapter::toDomain);
    }

    private static Query ownedLease(String eventId, String leaseToken) {
        return Query.query(Criteria.where("_id")
                .is(eventId)
                .and("state")
                .is(State.IN_FLIGHT.name())
                .and("leaseToken")
                .is(leaseToken));
    }

    private static InterestTagAlimtalkOutboxDocument toDocument(InterestTagAlimtalkEvent event) {
        return new InterestTagAlimtalkOutboxDocument(
                event.eventId(),
                event.type().name(),
                event.state().name(),
                event.attempts(),
                event.nextAttemptAt(),
                event.leaseToken(),
                event.leaseUntil(),
                event.lastErrorCode(),
                event.occurredAt(),
                event.createdAt(),
                event.completedAt(),
                event.listingId(),
                event.sellerId(),
                event.interestTagKey(),
                event.interestTagLabel(),
                event.listingTitle(),
                event.recipientAccountId(),
                event.cursorAccountId(),
                event.enqueuedRecipients(),
                null);
    }

    private static InterestTagAlimtalkEvent toDomain(InterestTagAlimtalkOutboxDocument document) {
        return new InterestTagAlimtalkEvent(
                document.getEventId(),
                Type.valueOf(document.getType()),
                State.valueOf(document.getState()),
                document.getAttempts(),
                document.getNextAttemptAt(),
                document.getLeaseToken(),
                document.getLeaseUntil(),
                document.getLastErrorCode(),
                document.getOccurredAt(),
                document.getCreatedAt(),
                document.getCompletedAt(),
                document.getListingId(),
                document.getSellerId(),
                document.getInterestTagKey(),
                document.getInterestTagLabel(),
                document.getListingTitle(),
                document.getRecipientAccountId(),
                document.getCursorAccountId(),
                document.getEnqueuedRecipients());
    }
}
