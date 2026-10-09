package com.gole.api.collection.adapter.out.persistence;

import com.gole.api.collection.application.port.out.CollectionRepositoryPort;
import com.gole.api.collection.domain.model.CollectionItem;
import com.gole.api.collection.domain.model.OwnershipStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * 컬렉션 영속성 어댑터. 도메인 {@link CollectionItem}과 {@link CollectionItemDocument}를 양방향 매핑한다.
 */
@Component
public class CollectionItemPersistenceAdapter implements CollectionRepositoryPort {

    private final CollectionItemMongoRepository repository;
    private final MongoTemplate mongoTemplate;

    public CollectionItemPersistenceAdapter(CollectionItemMongoRepository repository, MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public CollectionItem save(CollectionItem item) {
        CollectionItemDocument saved = repository.save(toDocument(item));
        return toDomain(saved);
    }

    @Override
    public Optional<CollectionItem> findById(String itemId) {
        return repository.findById(itemId).map(this::toDomain);
    }

    @Override
    public List<CollectionItem> findByUser(String userId) {
        return repository.findByUserId(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<String> findUserIdsBySetAndStatus(String setNumber, OwnershipStatus status) {
        return repository.findBySetNumberAndStatus(setNumber, status.name()).stream()
                .map(CollectionItemDocument::getUserId)
                .distinct()
                .toList();
    }

    @Override
    public List<String> findUserIdsWithStatus(OwnershipStatus status, String afterUserId, int limit) {
        // 첫 페이지에서도 userId가 없는 문서가 끼지 않게 문자열만 받는다. 끼면 페이지가 짧아져 끝으로 오인한다.
        Document match = new Document("status", status.name())
                .append(
                        "userId",
                        afterUserId == null ? new Document("$type", "string") : new Document("$gt", afterUserId));
        // 사용자당 항목이 여럿이라 distinct가 필요하고, 커서로 넘기려면 정렬이 필요하다. 집계 한 번으로 둘 다 한다.
        List<Document> pipeline = List.of(
                new Document("$match", match),
                new Document("$group", new Document("_id", "$userId")),
                new Document("$sort", new Document("_id", 1)),
                new Document("$limit", Math.max(1, limit)));
        List<String> userIds = new ArrayList<>();
        mongoTemplate
                .getCollection(mongoTemplate.getCollectionName(CollectionItemDocument.class))
                .aggregate(pipeline)
                .forEach(row -> {
                    Object userId = row.get("_id");
                    if (userId instanceof String id && !id.isBlank()) {
                        userIds.add(id);
                    }
                });
        return List.copyOf(userIds);
    }

    @Override
    public void delete(CollectionItem item) {
        repository.deleteById(item.id());
    }

    private CollectionItemDocument toDocument(CollectionItem item) {
        return new CollectionItemDocument(
                item.id(), item.userId(), item.setNumber(), item.status().name(), item.createdAt());
    }

    private CollectionItem toDomain(CollectionItemDocument document) {
        return new CollectionItem(
                document.getId(),
                document.getUserId(),
                document.getSetNumber(),
                OwnershipStatus.valueOf(document.getStatus()),
                document.getCreatedAt());
    }
}
