package com.gole.api.media.adapter.out.persistence;

import com.gole.api.media.application.port.out.MediaDeletionOutboxPort;
import com.gole.api.media.domain.model.MediaDeletionTask;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Component
public class MongoMediaDeletionOutboxAdapter implements MediaDeletionOutboxPort {

    private final MediaDeletionTaskMongoRepository repository;
    private final MongoTemplate mongoTemplate;

    public MongoMediaDeletionOutboxAdapter(MediaDeletionTaskMongoRepository repository, MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void enqueueIfAbsent(MediaDeletionTask task) {
        // duplicate-key 예외를 catch하면 Mongo 트랜잭션 자체가 abort될 수 있다. 조건부 upsert의
        // $setOnInsert를 써서 같은 폐기 전이를 예외 없이 원자적으로 흡수한다.
        Query query = Query.query(Criteria.where("mediaKey").is(task.mediaKey()));
        Update insert = new Update()
                .setOnInsert("_id", task.id())
                .setOnInsert("mediaKey", task.mediaKey())
                .setOnInsert("status", task.status().name())
                .setOnInsert("attempts", task.attempts())
                .setOnInsert("nextAttemptAt", task.nextAttemptAt())
                .setOnInsert("lastErrorCode", task.lastErrorCode())
                .setOnInsert("createdAt", task.createdAt())
                .setOnInsert("completedAt", task.completedAt());
        mongoTemplate.upsert(query, insert, MediaDeletionTaskDocument.class);
    }

    @Override
    public void requeue(MediaDeletionTask task) {
        Query query = Query.query(Criteria.where("mediaKey").is(task.mediaKey()));
        Update pending = new Update()
                .setOnInsert("_id", task.id())
                .setOnInsert("mediaKey", task.mediaKey())
                .setOnInsert("createdAt", task.createdAt())
                .set("status", MediaDeletionTask.Status.PENDING.name())
                .set("attempts", 0)
                .set("nextAttemptAt", task.nextAttemptAt())
                .unset("lastErrorCode")
                .unset("completedAt");
        mongoTemplate.upsert(query, pending, MediaDeletionTaskDocument.class);
    }

    @Override
    public List<MediaDeletionTask> findDue(Instant now, int limit) {
        return repository
                .findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        MediaDeletionTask.Status.PENDING.name(), now, PageRequest.of(0, limit))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<MediaDeletionTask> findCompletedSince(Instant since) {
        return repository
                .findByStatusAndCompletedAtGreaterThanEqualOrderByCompletedAtAsc(
                        MediaDeletionTask.Status.COMPLETED.name(), since)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void save(MediaDeletionTask task) {
        repository.save(toDocument(task));
    }

    private MediaDeletionTaskDocument toDocument(MediaDeletionTask task) {
        return new MediaDeletionTaskDocument(
                task.id(),
                task.mediaKey(),
                task.status().name(),
                task.attempts(),
                task.nextAttemptAt(),
                task.lastErrorCode(),
                task.createdAt(),
                task.completedAt());
    }

    private MediaDeletionTask toDomain(MediaDeletionTaskDocument document) {
        return new MediaDeletionTask(
                document.getId(),
                document.getMediaKey(),
                MediaDeletionTask.Status.valueOf(document.getStatus()),
                document.getAttempts(),
                document.getNextAttemptAt(),
                document.getLastErrorCode(),
                document.getCreatedAt(),
                document.getCompletedAt());
    }
}
