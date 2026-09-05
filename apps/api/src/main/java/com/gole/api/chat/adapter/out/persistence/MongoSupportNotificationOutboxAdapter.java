package com.gole.api.chat.adapter.out.persistence;

import com.gole.api.chat.application.SupportNotificationOutboxProperties;
import com.gole.api.chat.application.port.out.SupportNotificationOutboxPort;
import com.gole.api.chat.domain.model.SupportCategory;
import com.gole.api.chat.domain.model.SupportNotificationEvent;
import com.gole.api.chat.domain.model.SupportNotificationEvent.EventType;
import com.gole.api.chat.domain.model.SupportNotificationEvent.State;
import com.gole.api.chat.domain.model.SupportStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/** 다중 worker에서도 한 lease만 발급하는 Mongo support 알림 outbox. */
@Component
public class MongoSupportNotificationOutboxAdapter implements SupportNotificationOutboxPort {

    private final MongoTemplate mongo;
    private final SupportNotificationOutboxProperties properties;

    public MongoSupportNotificationOutboxAdapter(MongoTemplate mongo, SupportNotificationOutboxProperties properties) {
        this.mongo = mongo;
        this.properties = properties;
    }

    @Override
    public void enqueue(SupportNotificationEvent event) {
        mongo.insert(toDocument(event));
    }

    @Override
    public Optional<SupportNotificationEvent> claimNext(Instant now, Duration leaseDuration, int maximumAttempts) {
        // 마지막 허용 시도 중 프로세스가 죽은 항목은 영원히 IN_FLIGHT로 남지 않게 별도
        // dead-letter로 닫는다. 응답 본문이나 예외 원문은 저장하지 않는다.
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
                        .set("expiresAt", now.plus(properties.getTerminalRetention()))
                        .unset("nextAttemptAt")
                        .unset("leaseToken")
                        .unset("leaseUntil"),
                SupportNotificationOutboxDocument.class);

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
        SupportNotificationOutboxDocument claimed = mongo.findAndModify(
                due, claim, FindAndModifyOptions.options().returnNew(true), SupportNotificationOutboxDocument.class);
        return Optional.ofNullable(claimed).map(MongoSupportNotificationOutboxAdapter::toDomain);
    }

    @Override
    public void delivered(String eventId, String leaseToken, Instant deliveredAt) {
        mongo.updateFirst(
                ownedLease(eventId, leaseToken),
                new Update()
                        .set("state", State.DELIVERED.name())
                        .set("deliveredAt", deliveredAt)
                        .set("expiresAt", deliveredAt.plus(properties.getTerminalRetention()))
                        .unset("nextAttemptAt")
                        .unset("leaseToken")
                        .unset("leaseUntil")
                        .unset("lastErrorCode"),
                SupportNotificationOutboxDocument.class);
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
            update.set("expiresAt", failedAt.plus(properties.getTerminalRetention()));
        } else {
            update.set("nextAttemptAt", nextAttemptAt);
            update.unset("expiresAt");
        }
        mongo.updateFirst(ownedLease(eventId, leaseToken), update, SupportNotificationOutboxDocument.class);
    }

    @Override
    public Optional<SupportNotificationEvent> requeueDeadLetter(String eventId, Instant requeuedAt) {
        Query deadLetter =
                Query.query(Criteria.where("_id").is(eventId).and("state").is(State.DEAD_LETTER.name()));
        Update requeue = new Update()
                .set("state", State.PENDING.name())
                .set("attempts", 0)
                .set("nextAttemptAt", requeuedAt)
                .unset("leaseToken")
                .unset("leaseUntil")
                .unset("lastErrorCode")
                .unset("deliveredAt")
                .unset("expiresAt");
        SupportNotificationOutboxDocument updated = mongo.findAndModify(
                deadLetter,
                requeue,
                FindAndModifyOptions.options().returnNew(true),
                SupportNotificationOutboxDocument.class);
        return Optional.ofNullable(updated).map(MongoSupportNotificationOutboxAdapter::toDomain);
    }

    @Override
    public Optional<SupportNotificationEvent> findById(String eventId) {
        return Optional.ofNullable(mongo.findById(eventId, SupportNotificationOutboxDocument.class))
                .map(MongoSupportNotificationOutboxAdapter::toDomain);
    }

    private static Query ownedLease(String eventId, String leaseToken) {
        return Query.query(Criteria.where("_id")
                .is(eventId)
                .and("state")
                .is(State.IN_FLIGHT.name())
                .and("leaseToken")
                .is(leaseToken));
    }

    private static SupportNotificationOutboxDocument toDocument(SupportNotificationEvent event) {
        return new SupportNotificationOutboxDocument(
                event.eventId(),
                event.type().name(),
                event.supportCategory().name(),
                event.ticketStatus().name(),
                event.state().name(),
                event.attempts(),
                event.nextAttemptAt(),
                event.leaseToken(),
                event.leaseUntil(),
                event.lastErrorCode(),
                event.occurredAt(),
                event.createdAt(),
                event.deliveredAt(),
                null);
    }

    private static SupportNotificationEvent toDomain(SupportNotificationOutboxDocument document) {
        return new SupportNotificationEvent(
                document.getEventId(),
                EventType.valueOf(document.getType()),
                SupportCategory.valueOf(document.getSupportCategory()),
                SupportStatus.valueOf(document.getTicketStatus()),
                State.valueOf(document.getState()),
                document.getAttempts(),
                document.getNextAttemptAt(),
                document.getLeaseToken(),
                document.getLeaseUntil(),
                document.getLastErrorCode(),
                document.getOccurredAt(),
                document.getCreatedAt(),
                document.getDeliveredAt());
    }
}
