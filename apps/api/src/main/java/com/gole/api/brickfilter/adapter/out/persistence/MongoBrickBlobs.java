package com.gole.api.brickfilter.adapter.out.persistence;

import com.gole.api.brickfilter.application.port.out.BrickBlobPort;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Repository;

/** Private bounded blobs; never routed through generic media keys, URLs or object ACLs. */
@Repository
public class MongoBrickBlobs implements BrickBlobPort {
    private final MongoTemplate mongo;
    private final java.util.concurrent.atomic.AtomicBoolean indexed = new java.util.concurrent.atomic.AtomicBoolean();

    public MongoBrickBlobs(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Document("brick_filter_blobs")
    public record Blob(@Id String id, String owner, String job, String kind, byte[] content, Instant expiresAt) {}

    private String key(String owner, String job, String kind) {
        return owner + ":" + job + ":" + kind;
    }

    public void put(String owner, String job, String kind, byte[] data, Instant expiry) {
        if (!indexed.get()) {
            mongo.indexOps(Blob.class)
                    .createIndex(new org.springframework.data.mongodb.core.index.Index()
                            .on("expiresAt", org.springframework.data.domain.Sort.Direction.ASC)
                            .expire(java.time.Duration.ZERO));
            indexed.set(true);
        }
        if (data.length > 8 * 1024 * 1024) throw new IllegalArgumentException("blob too large");
        mongo.insert(new Blob(key(owner, job, kind), owner, job, kind, data, expiry));
    }

    public Optional<byte[]> get(String owner, String job, String kind, Instant now) {
        return Optional.ofNullable(mongo.findOne(
                        Query.query(Criteria.where("_id")
                                .is(key(owner, job, kind))
                                .and("owner")
                                .is(owner)
                                .and("expiresAt")
                                .gt(now)),
                        Blob.class))
                .map(Blob::content);
    }

    public void delete(String owner, String job, String kind) {
        mongo.remove(
                Query.query(Criteria.where("_id")
                        .is(key(owner, job, kind))
                        .and("owner")
                        .is(owner)),
                Blob.class);
    }

    public void purge(Instant now) {
        mongo.remove(Query.query(Criteria.where("expiresAt").lte(now)), Blob.class);
    }
}
