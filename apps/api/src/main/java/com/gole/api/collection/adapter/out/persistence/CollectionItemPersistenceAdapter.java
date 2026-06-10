package com.gole.api.collection.adapter.out.persistence;

import com.gole.api.collection.application.port.out.CollectionRepositoryPort;
import com.gole.api.collection.domain.model.CollectionItem;
import com.gole.api.collection.domain.model.OwnershipStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 컬렉션 영속성 어댑터. 도메인 {@link CollectionItem}과 {@link CollectionItemDocument}를 양방향 매핑한다.
 */
@Component
public class CollectionItemPersistenceAdapter implements CollectionRepositoryPort {

    private final CollectionItemMongoRepository repository;

    public CollectionItemPersistenceAdapter(CollectionItemMongoRepository repository) {
        this.repository = repository;
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
