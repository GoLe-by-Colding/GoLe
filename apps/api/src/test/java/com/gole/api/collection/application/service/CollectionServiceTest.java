package com.gole.api.collection.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.collection.application.port.in.ManageCollectionUseCase.AddCommand;
import com.gole.api.collection.application.port.out.CollectionIdGeneratorPort;
import com.gole.api.collection.application.port.out.CollectionRepositoryPort;
import com.gole.api.collection.application.port.out.LatestPriceProviderPort;
import com.gole.api.collection.domain.model.CollectionItem;
import com.gole.api.collection.domain.model.OwnershipStatus;
import com.gole.api.common.exception.ForbiddenException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CollectionServiceTest {

    private InMemoryRepo repo;
    private CollectionService service;

    @BeforeEach
    void setUp() {
        repo = new InMemoryRepo();
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        // 10307 -> 280000, 75313 -> 600000, 그 외 없음
        LatestPriceProviderPort prices = setNumber ->
                Optional.ofNullable(Map.of("10307", 280_000L, "75313", 600_000L).get(setNumber));
        service = new CollectionService(repo, prices, new SeqIds(), clock);
    }

    @Test
    void add_and_getCollection() {
        service.add(new AddCommand("u1", "10307", OwnershipStatus.OWNED));
        assertThat(service.getCollection("u1")).hasSize(1);
    }

    @Test
    void remove_byNonOwner_isForbidden() {
        String id = service.add(new AddCommand("u1", "10307", OwnershipStatus.OWNED));
        assertThatThrownBy(() -> service.remove(id, "intruder")).isInstanceOf(ForbiddenException.class);
        service.remove(id, "u1");
        assertThat(service.getCollection("u1")).isEmpty();
    }

    @Test
    void estimate_sumsOnlyOwnedWithKnownPrices() {
        service.add(new AddCommand("u1", "10307", OwnershipStatus.OWNED)); // 280000
        service.add(new AddCommand("u1", "75313", OwnershipStatus.OWNED)); // 600000
        service.add(new AddCommand("u1", "99999", OwnershipStatus.OWNED)); // 가격 없음 → 0
        service.add(new AddCommand("u1", "10307", OwnershipStatus.WANTED)); // owned 아님 → 제외
        assertThat(service.estimateOwnedValue("u1")).isEqualTo(880_000L);
    }

    private static final class InMemoryRepo implements CollectionRepositoryPort {
        private final List<CollectionItem> store = new ArrayList<>();

        @Override
        public CollectionItem save(CollectionItem item) {
            store.add(item);
            return item;
        }

        @Override
        public Optional<CollectionItem> findById(String itemId) {
            return store.stream().filter(i -> i.id().equals(itemId)).findFirst();
        }

        @Override
        public List<CollectionItem> findByUser(String userId) {
            return store.stream().filter(i -> i.userId().equals(userId)).toList();
        }

        @Override
        public void delete(CollectionItem item) {
            store.removeIf(i -> i.id().equals(item.id()));
        }
    }

    private static final class SeqIds implements CollectionIdGeneratorPort {
        private final AtomicInteger n = new AtomicInteger();

        @Override
        public String newId() {
            return "ci-" + n.incrementAndGet();
        }
    }
}
