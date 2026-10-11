package com.gole.api.collection;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.collection.application.port.out.CollectionRepositoryPort;
import com.gole.api.collection.application.port.out.CollectionValueSnapshotRepositoryPort;
import com.gole.api.collection.domain.model.CollectionItem;
import com.gole.api.collection.domain.model.CollectionValueSnapshot;
import com.gole.api.collection.domain.model.OwnershipStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 자산 추이 스냅샷 upsert·범위 조회와 보유 사용자 커서를 실제 MongoDB로 확인한다.
 * (collection-value-history H1, H2, H6)
 */
@SpringBootTest
@Testcontainers
class CollectionValueSnapshotPersistenceIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-03-11T00:00:00Z");
    private static final LocalDate DAY = LocalDate.of(2026, 3, 11);

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("gole.catalog.seed-on-empty", () -> "false");
        registry.add("gole.listing.seed-on-empty", () -> "false");
        registry.add("gole.pricing.seed-on-empty", () -> "false");
        registry.add("gole.community.seed-on-empty", () -> "false");
        registry.add("gole.report.seed-on-empty", () -> "false");
        registry.add("gole.review.seed-on-empty", () -> "false");
        registry.add("gole.media.seed-on-startup", () -> "false");
        registry.add("gole.support-notification-outbox.processing-enabled", () -> "false");
        registry.add("gole.collection.value-snapshot.enabled", () -> "false");
    }

    @Autowired
    CollectionValueSnapshotRepositoryPort snapshots;

    @Autowired
    CollectionRepositoryPort collections;

    @Autowired
    MongoTemplate mongo;

    @BeforeEach
    void clean() {
        mongo.getDb().getCollection("collection_value_snapshots").deleteMany(new Document());
        mongo.getDb().getCollection("collection_items").deleteMany(new Document());
    }

    @Test
    void upsertKeepsOneDocumentPerUserAndDayAndOverwritesValues() {
        snapshots.upsert(new CollectionValueSnapshot("u1", DAY, 100_000, 2, 1, T0));
        snapshots.upsert(new CollectionValueSnapshot("u1", DAY, 280_000, 3, 2, T0.plusSeconds(60)));
        snapshots.upsert(new CollectionValueSnapshot("u2", DAY, 50_000, 1, 1, T0));

        assertThat(mongo.getDb()
                        .getCollection("collection_value_snapshots")
                        .countDocuments(new Document("userId", "u1")))
                .isEqualTo(1);
        CollectionValueSnapshot stored = snapshots.findRange("u1", DAY, DAY).getFirst();
        assertThat(stored.ownedValue()).isEqualTo(280_000);
        assertThat(stored.ownedCount()).isEqualTo(3);
        assertThat(stored.pricedCount()).isEqualTo(2);
        assertThat(stored.capturedAt()).isEqualTo(T0.plusSeconds(60));
    }

    @Test
    void findRangeIsInclusiveAscendingAndScopedToUser() {
        for (int offset = 0; offset < 5; offset++) {
            snapshots.upsert(new CollectionValueSnapshot("u1", DAY.minusDays(offset), offset * 1_000L, 1, 1, T0));
        }
        snapshots.upsert(new CollectionValueSnapshot("u2", DAY.minusDays(1), 999, 1, 1, T0));

        List<CollectionValueSnapshot> range = snapshots.findRange("u1", DAY.minusDays(3), DAY.minusDays(1));

        assertThat(range)
                .extracting(CollectionValueSnapshot::date)
                .containsExactly(DAY.minusDays(3), DAY.minusDays(2), DAY.minusDays(1));
        assertThat(range).extracting(CollectionValueSnapshot::userId).containsOnly("u1");
    }

    @Test
    void snapshotCollectionHasUniqueUserDateAndCapturedAtTtlIndexes() {
        List<Document> indexes = new ArrayList<>();
        mongo.getDb().getCollection("collection_value_snapshots").listIndexes().into(indexes);

        assertThat(indexes).anySatisfy(index -> {
            assertThat(index.get("key", Document.class)).isEqualTo(new Document("userId", 1).append("date", 1));
            assertThat(index.getBoolean("unique")).isTrue();
        });
        assertThat(indexes).anySatisfy(index -> {
            assertThat(index.get("key", Document.class)).isEqualTo(new Document("capturedAt", 1));
            assertThat(((Number) index.get("expireAfterSeconds")).longValue()).isEqualTo(400L * 24 * 60 * 60);
        });
    }

    @Test
    void ownerCursorReturnsDistinctSortedUsersPageByPage() {
        save("i1", "carol", OwnershipStatus.OWNED);
        save("i2", "alice", OwnershipStatus.OWNED);
        save("i3", "alice", OwnershipStatus.OWNED); // 같은 사용자 항목 둘 — 한 번만 나와야 한다
        save("i4", "bob", OwnershipStatus.WANTED); // 보유가 아니면 대상 아님
        save("i5", "dave", OwnershipStatus.OWNED);
        save("i6", "erin", OwnershipStatus.OWNED);

        List<String> first = collections.findUserIdsWithStatus(OwnershipStatus.OWNED, null, 2);
        List<String> second = collections.findUserIdsWithStatus(OwnershipStatus.OWNED, first.getLast(), 2);
        List<String> third = collections.findUserIdsWithStatus(OwnershipStatus.OWNED, second.getLast(), 2);

        assertThat(first).containsExactly("alice", "carol");
        assertThat(second).containsExactly("dave", "erin");
        assertThat(third).isEmpty();
    }

    private void save(String id, String userId, OwnershipStatus status) {
        collections.save(new CollectionItem(id, userId, "10307", status, T0));
    }
}
