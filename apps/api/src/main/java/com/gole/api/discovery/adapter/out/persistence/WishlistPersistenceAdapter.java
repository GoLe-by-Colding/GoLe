package com.gole.api.discovery.adapter.out.persistence;

import com.gole.api.discovery.application.port.out.WishlistRepositoryPort;
import com.gole.api.discovery.domain.exception.DuplicateWishlistException;
import com.gole.api.discovery.domain.model.WishlistEntry;
import com.gole.api.discovery.domain.model.WishlistTargetType;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * 위시리스트 영속성 어댑터. 도메인 {@link WishlistEntry}와 {@link WishlistEntryDocument}를 매핑한다.
 * {@link WishlistTargetType}은 enum 이름으로 저장/복원한다.
 */
@Component
public class WishlistPersistenceAdapter implements WishlistRepositoryPort {

    private final WishlistMongoRepository repository;

    public WishlistPersistenceAdapter(WishlistMongoRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean exists(String userId, WishlistTargetType type, String targetId) {
        return repository.existsByUserIdAndTargetTypeAndTargetId(userId, type.name(), targetId);
    }

    @Override
    public void save(WishlistEntry entry) {
        try {
            repository.save(toDocument(entry));
        } catch (DuplicateKeyException ex) {
            throw new DuplicateWishlistException();
        }
    }

    @Override
    public void delete(String userId, WishlistTargetType type, String targetId) {
        repository.deleteByUserIdAndTargetTypeAndTargetId(userId, type.name(), targetId);
    }

    @Override
    public List<WishlistEntry> findByUser(String userId) {
        return repository.findByUserId(userId).stream().map(this::toDomain).toList();
    }

    private WishlistEntryDocument toDocument(WishlistEntry entry) {
        // id 는 MongoDB가 생성하도록 null 로 둔다.
        return new WishlistEntryDocument(
                null, entry.userId(), entry.targetType().name(), entry.targetId());
    }

    private WishlistEntry toDomain(WishlistEntryDocument document) {
        return new WishlistEntry(
                document.getUserId(), WishlistTargetType.valueOf(document.getTargetType()), document.getTargetId());
    }
}
