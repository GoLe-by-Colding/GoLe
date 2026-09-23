package com.gole.api.common.operations.sentry;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/** _id upsert는 페이지 재조회에 멱등이고 lease 소유자만 진행점을 바꿀 수 있다. */
@Component
public class MongoSentryPollStore implements SentryPollStore {
    private static final String KEY = "gole-web-production";
    private final MongoTemplate mongo;

    public MongoSentryPollStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public Lease acquire(Instant now) {
        mongo.upsert(
                Query.query(Criteria.where("_id").is(KEY)),
                new Update()
                        .setOnInsert("from", now.minus(Duration.ofMinutes(5)))
                        .setOnInsert("leaseUntil", Instant.EPOCH)
                        .setOnInsert("readComplete", false),
                PollDocument.class);
        String owner = UUID.randomUUID().toString();
        PollDocument doc = mongo.findAndModify(
                Query.query(Criteria.where("_id").is(KEY).and("leaseUntil").lte(now)),
                new Update().set("owner", owner).set("leaseUntil", now.plusSeconds(120)),
                FindAndModifyOptions.options().returnNew(true),
                PollDocument.class);
        if (doc == null) return null;
        return new Lease(
                owner,
                new Scan(doc.from(), doc.until(), doc.cursor(), doc.readComplete()),
                doc.readNotBefore() == null ? Instant.EPOCH : doc.readNotBefore());
    }

    @Override
    public void saveScan(Lease lease, Scan scan) {
        long matched = mongo.updateFirst(
                        owned(lease),
                        new Update()
                                .set("from", scan.from())
                                .set("until", scan.until())
                                .set("cursor", scan.cursor())
                                .set("readComplete", scan.readComplete()),
                        PollDocument.class)
                .getMatchedCount();
        if (matched != 1) throw new IllegalStateException("SENTRY_LEASE_LOST");
    }

    @Override
    public void deferRead(Lease lease, Instant until) {
        if (mongo.updateFirst(owned(lease), new Update().set("readNotBefore", until), PollDocument.class)
                        .getMatchedCount()
                != 1) throw new IllegalStateException("SENTRY_LEASE_LOST");
    }

    @Override
    public void enqueue(String key, Instant occurredAt) {
        mongo.upsert(
                Query.query(Criteria.where("_id").is(key)),
                new Update()
                        .setOnInsert("occurredAt", occurredAt)
                        .setOnInsert("retryAt", Instant.EPOCH)
                        .setOnInsert("delivered", false)
                        .setOnInsert("attempts", 0),
                AlertDocument.class);
    }

    @Override
    public List<Alert> pending(Instant now) {
        return mongo
                .find(
                        Query.query(Criteria.where("delivered")
                                        .is(false)
                                        .and("retryAt")
                                        .lte(now))
                                .limit(10),
                        AlertDocument.class)
                .stream()
                .map(row -> new Alert(row.id(), row.occurredAt(), row.attempts()))
                .toList();
    }

    @Override
    public void delivered(String key) {
        if (mongo.updateFirst(
                                Query.query(Criteria.where("_id").is(key)),
                                new Update().set("delivered", true),
                                AlertDocument.class)
                        .getMatchedCount()
                != 1) throw new IllegalStateException("SENTRY_ALERT_MISSING");
    }

    @Override
    public void retry(String key, Instant retryAt) {
        mongo.updateFirst(
                Query.query(Criteria.where("_id").is(key).and("delivered").is(false)),
                new Update().set("retryAt", retryAt).inc("attempts", 1),
                AlertDocument.class);
    }

    @Override
    public boolean hasPending() {
        return mongo.exists(Query.query(Criteria.where("delivered").is(false)), AlertDocument.class);
    }

    @Override
    public void release(Lease lease) {
        mongo.updateFirst(owned(lease), new Update().set("leaseUntil", Instant.EPOCH), PollDocument.class);
    }

    private Query owned(Lease lease) {
        return Query.query(Criteria.where("_id").is(KEY).and("owner").is(lease.owner()));
    }

    @Document("sentry_web_poll")
    record PollDocument(
            @Id String id,
            Instant from,
            Instant until,
            String cursor,
            boolean readComplete,
            String owner,
            Instant leaseUntil,
            Instant readNotBefore) {}

    @Document("sentry_web_alerts")
    record AlertDocument(@Id String id, Instant occurredAt, Instant retryAt, boolean delivered, int attempts) {}
}
