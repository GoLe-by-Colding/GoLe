package com.gole.api.collection.application.service;

import com.gole.api.collection.application.port.in.EstimateCollectionValueUseCase;
import com.gole.api.collection.application.port.in.ManageCollectionUseCase;
import com.gole.api.collection.application.port.out.CollectionIdGeneratorPort;
import com.gole.api.collection.application.port.out.CollectionRepositoryPort;
import com.gole.api.collection.application.port.out.LatestPriceProviderPort;
import com.gole.api.collection.domain.exception.CollectionItemNotFoundException;
import com.gole.api.collection.domain.model.CollectionItem;
import com.gole.api.common.exception.ForbiddenException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 컬렉션 유스케이스 구현. inbound port를 구현하고 outbound port에만 의존한다.
 */
@Service
public class CollectionService
        implements ManageCollectionUseCase, EstimateCollectionValueUseCase {

    private final CollectionRepositoryPort repository;
    private final LatestPriceProviderPort latestPriceProvider;
    private final CollectionIdGeneratorPort idGenerator;
    private final Clock clock;

    public CollectionService(
            CollectionRepositoryPort repository,
            LatestPriceProviderPort latestPriceProvider,
            CollectionIdGeneratorPort idGenerator,
            Clock clock) {
        this.repository = repository;
        this.latestPriceProvider = latestPriceProvider;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    public String add(AddCommand command) {
        CollectionItem item = new CollectionItem(
                idGenerator.newId(),
                command.userId(),
                command.setNumber(),
                command.status(),
                Instant.now(clock));
        return repository.save(item).id();
    }

    @Override
    public List<CollectionItem> getCollection(String userId) {
        return repository.findByUser(userId);
    }

    @Override
    public void remove(String itemId, String userId) {
        CollectionItem item = repository.findById(itemId)
                .orElseThrow(() -> new CollectionItemNotFoundException(itemId));
        if (!item.userId().equals(userId)) {
            throw new ForbiddenException("NOT_COLLECTION_OWNER", "Only the owner can remove this item");
        }
        repository.delete(item);
    }

    @Override
    public long estimateOwnedValue(String userId) {
        // 요구사항 11.5: 보유 항목의 최근 체결가 합산(가격 없는 세트는 0).
        return repository.findByUser(userId).stream()
                .filter(CollectionItem::isOwned)
                .mapToLong(item -> latestPriceProvider.latestPrice(item.setNumber()).orElse(0L))
                .sum();
    }
}
