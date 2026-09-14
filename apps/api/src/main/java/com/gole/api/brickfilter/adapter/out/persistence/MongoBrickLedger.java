package com.gole.api.brickfilter.adapter.out.persistence;

import com.gole.api.brickfilter.application.port.out.BrickLedgerPort;
import com.gole.api.brickfilter.domain.model.BrickJob;
import java.time.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Repository;

@Repository
public class MongoBrickLedger implements BrickLedgerPort {
    private final MongoTemplate mongo;
    private final java.util.concurrent.atomic.AtomicBoolean indexed = new java.util.concurrent.atomic.AtomicBoolean();

    public MongoBrickLedger(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Document("brick_filter_days")
    public record Day(
            @Id String id, String owner, String day, int occupied, int attempts, List<BrickJob> jobs, Instant purgeAt) {
        Ledger model() {
            return new Ledger(owner, day, occupied, attempts, jobs);
        }
    }

    private String key(String owner, String day) {
        return owner + ":" + day;
    }

    public Ledger load(String owner, String day) {
        var d = mongo.findById(key(owner, day), Day.class);
        return d == null ? new Ledger(owner, day, 0, 0, List.of()) : d.model();
    }

    public boolean reserve(String owner, String day, BrickJob job) {
        if (!indexed.get()) {
            mongo.indexOps(Day.class)
                    .createIndex(new org.springframework.data.mongodb.core.index.Index()
                            .on("purgeAt", org.springframework.data.domain.Sort.Direction.ASC)
                            .expire(java.time.Duration.ZERO));
            indexed.set(true);
        }
        var id = key(owner, day);
        try {
            mongo.insert(new Day(
                    id,
                    owner,
                    day,
                    0,
                    0,
                    List.of(),
                    LocalDate.parse(day)
                            .plusDays(8)
                            .atStartOfDay(ZoneId.of("Asia/Seoul"))
                            .toInstant()));
        } catch (DuplicateKeyException ignored) {
        }
        var q = Query.query(Criteria.where("_id")
                .is(id)
                .and("occupied")
                .lt(3)
                .and("attempts")
                .lt(30)
                .and("jobs.id")
                .ne(job.id()));
        return mongo.updateFirst(
                                q,
                                new Update()
                                        .inc("occupied", 1)
                                        .inc("attempts", 1)
                                        .push("jobs", job),
                                Day.class)
                        .getModifiedCount()
                == 1;
    }

    public boolean complete(String owner, String day, String id, Instant now) {
        var q = Query.query(Criteria.where("_id")
                .is(key(owner, day))
                .and("jobs")
                .elemMatch(Criteria.where("id")
                        .is(id)
                        .and("status")
                        .is("RESERVED")
                        .and("leaseUntil")
                        .gt(now)));
        return mongo.updateFirst(q, new Update().set("jobs.$.status", "SUCCEEDED"), Day.class)
                        .getModifiedCount()
                == 1;
    }

    public boolean fail(String owner, String day, String id) {
        var q = Query.query(Criteria.where("_id")
                .is(key(owner, day))
                .and("jobs")
                .elemMatch(Criteria.where("id").is(id).and("status").is("RESERVED")));
        return mongo.updateFirst(q, new Update().set("jobs.$.status", "FAILED").inc("occupied", -1), Day.class)
                        .getModifiedCount()
                == 1;
    }

    public List<Ledger> expired(Instant now) {
        return mongo
                .find(
                        Query.query(Criteria.where("jobs")
                                        .elemMatch(Criteria.where("status")
                                                .is("RESERVED")
                                                .and("leaseUntil")
                                                .lte(now)))
                                .limit(100),
                        Day.class)
                .stream()
                .map(Day::model)
                .toList();
    }

    public void purge(Instant now) {
        mongo.remove(Query.query(Criteria.where("purgeAt").lte(now)), Day.class);
    }
}
