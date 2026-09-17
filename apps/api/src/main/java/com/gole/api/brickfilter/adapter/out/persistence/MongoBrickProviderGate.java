package com.gole.api.brickfilter.adapter.out.persistence;

import com.gole.api.brickfilter.application.port.out.BrickProviderGatePort;
import java.time.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Repository;

@Repository
public class MongoBrickProviderGate implements BrickProviderGatePort {
    private final MongoTemplate mongo;

    public MongoBrickProviderGate(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public record Lease(String token, Instant until) {}

    @Document("brick_filter_provider_gate")
    public record Gate(
            @Id String id, long revision, long minute, int minuteCalls, String day, int dayCalls, List<Lease> leases) {}

    public boolean acquire(String token, Instant now) {
        long minute = now.getEpochSecond() / 60;
        String day = now.atZone(ZoneId.of("Asia/Seoul")).toLocalDate().toString();
        try {
            mongo.insert(new Gate("global", 0, minute, 0, day, 0, List.of()));
        } catch (DuplicateKeyException ignored) {
        }
        for (int retry = 0; retry < 10; retry++) {
            Gate current = mongo.findById("global", Gate.class);
            if (current == null) return false;
            var leases = new ArrayList<>(current.leases().stream()
                    .filter(l -> l.until().isAfter(now))
                    .toList());
            int minuteCalls = current.minute() == minute ? current.minuteCalls() : 0;
            int dayCalls = current.day().equals(day) ? current.dayCalls() : 0;
            // Deliberately conservative, fail closed; changing budgets requires a reviewed code change.
            if (leases.size() >= 2 || minuteCalls >= 6 || dayCalls >= 100) return false;
            leases.add(new Lease(token, now.plusSeconds(300)));
            var update = new Update()
                    .inc("revision", 1)
                    .set("minute", minute)
                    .set("minuteCalls", minuteCalls + 1)
                    .set("day", day)
                    .set("dayCalls", dayCalls + 1)
                    .set("leases", leases);
            if (mongo.updateFirst(
                                    Query.query(Criteria.where("_id")
                                            .is("global")
                                            .and("revision")
                                            .is(current.revision())),
                                    update,
                                    Gate.class)
                            .getModifiedCount()
                    == 1) return true;
        }
        return false;
    }

    public void release(String token) {
        // Increment revision so a concurrent acquisition cannot restore a removed lease.
        mongo.updateFirst(
                Query.query(Criteria.where("_id").is("global")),
                new Update()
                        .pull("leases", new org.bson.Document("token", token))
                        .inc("revision", 1),
                Gate.class);
    }
}
