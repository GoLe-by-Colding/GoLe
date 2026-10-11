package com.gole.api.listing;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.listing.adapter.out.persistence.ListingListedAtBackfill;
import com.gole.api.listing.application.port.out.ListingRepositoryPort;
import com.gole.api.listing.application.query.ListingSearchQuery;
import com.gole.api.listing.domain.model.Completeness;
import com.gole.api.listing.domain.model.ConditionDisclosure;
import com.gole.api.listing.domain.model.InterestTag;
import com.gole.api.listing.domain.model.ItemCondition;
import com.gole.api.listing.domain.model.Listing;
import com.gole.api.listing.domain.model.ListingCategory;
import com.gole.api.listing.domain.model.ListingRevision;
import com.gole.api.listing.domain.model.ListingStatus;
import com.gole.api.listing.domain.model.Money;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
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
 * 끌올 정렬 키 백필·정렬과 수정의 원자 갱신을 실제 MongoDB로 확인한다. (listing-edit-and-bump B5, B6, E4)
 */
@SpringBootTest
@Testcontainers
class ListingEditAndBumpPersistenceIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-09-01T00:00:00Z");

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
    }

    @Autowired
    ListingRepositoryPort listings;

    @Autowired
    ListingListedAtBackfill backfill;

    @Autowired
    MongoTemplate mongo;

    @BeforeEach
    void clean() {
        mongo.getDb().getCollection("listings").deleteMany(new Document());
    }

    @Test
    void legacyDocumentsGetListedAtFromCreatedAtAndSortAlongsideBumpedListings() {
        insertLegacy("legacy-old", T0);
        insertLegacy("legacy-new", T0.plus(Duration.ofHours(1)));
        listings.save(listing("fresh", "seller-1", T0.plus(Duration.ofMinutes(30))));
        // 끌올 도입 전 문서는 백필 전에도 읽기 경로에서 createdAt으로 보인다. (B6)
        assertThat(listings.findById("legacy-old").orElseThrow().getListedAt()).isEqualTo(T0);

        long filled = backfill.backfill();

        assertThat(filled).isEqualTo(2);
        assertThat(rawListedAt("legacy-old")).isEqualTo(Date.from(T0));
        assertThat(rawListedAt("legacy-new")).isEqualTo(Date.from(T0.plus(Duration.ofHours(1))));
        assertThat(backfill.backfill()).as("멱등").isZero();

        assertThat(ids(listings.search(ListingSearchQuery.newestAll())))
                .containsExactly("legacy-new", "fresh", "legacy-old");

        Instant bumpedAt = T0.plus(Duration.ofDays(2));
        assertThat(listings.bumpIfActive("legacy-old", bumpedAt)).isTrue();
        assertThat(backfill.backfill()).as("끌올한 정렬 키를 되돌리지 않는다").isZero();

        Listing bumped = listings.findById("legacy-old").orElseThrow();
        assertThat(bumped.getListedAt()).isEqualTo(bumpedAt);
        assertThat(bumped.getBumpedAt()).isEqualTo(bumpedAt);
        assertThat(bumped.getCreatedAt()).isEqualTo(T0);
        assertThat(ids(listings.search(ListingSearchQuery.newestAll())))
                .containsExactly("legacy-old", "legacy-new", "fresh");
        assertThat(ids(listings.findBySeller("seller-1"))).containsExactly("legacy-old", "legacy-new", "fresh");
        assertThat(ids(listings.findActiveBySeller("seller-1"))).containsExactly("legacy-old", "legacy-new", "fresh");
        assertThat(ids(listings.findActiveBySellers(List.of("seller-1"), 10)))
                .containsExactly("legacy-old", "legacy-new", "fresh");
    }

    @Test
    void statusListedAtIndexExists() {
        List<String> names = new ArrayList<>();
        mongo.getCollection("listings").listIndexes().forEach(index -> names.add(index.getString("name")));

        assertThat(names).contains("ix_status_listedAt");
    }

    @Test
    void reviseLosesToReservationTakenAfterItWasRead() {
        listings.save(listing("contested", "seller-1", T0));
        Listing readBySeller = listings.findById("contested").orElseThrow();

        // 판매자가 수정 폼을 제출하는 사이 구매자의 주문이 먼저 예약을 잡는다.
        assertThat(listings.reserveIfActive("contested")).isPresent();
        readBySeller.revise(revision(9_000), T0.plusSeconds(10));
        boolean applied = listings.updateIfActive(readBySeller);

        assertThat(applied).isFalse();
        Listing stored = listings.findById("contested").orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(ListingStatus.RESERVED);
        assertThat(stored.getPrice().amount()).isEqualTo(10_000);
        assertThat(stored.getTitle()).isEqualTo("원래 제목");
        assertThat(listings.bumpIfActive("contested", T0.plus(Duration.ofDays(1))))
                .isFalse();
    }

    @Test
    void updateIfActiveWritesRevisionAndPriceHistoryButNotStatusSetOrListedAt() {
        listings.save(listing("revised", "seller-1", T0));
        Listing listing = listings.findById("revised").orElseThrow();
        listing.revise(revision(9_000), T0.plusSeconds(10));

        assertThat(listings.updateIfActive(listing)).isTrue();
        // 같은 값으로 다시 제출해도(변경 문서 0건) 성공이다.
        assertThat(listings.updateIfActive(listing)).isTrue();

        Listing stored = listings.findById("revised").orElseThrow();
        assertThat(stored.getTitle()).isEqualTo("수정한 제목");
        assertThat(stored.getPrice().amount()).isEqualTo(9_000);
        assertThat(stored.getPreviousPrice().amount()).isEqualTo(10_000);
        assertThat(stored.getPriceChangedAt()).isEqualTo(T0.plusSeconds(10));
        assertThat(stored.getCondition()).isEqualTo(ItemCondition.LIKE_NEW);
        assertThat(stored.getDisclosure().completeness()).isEqualTo(Completeness.FULL_BOX);
        assertThat(stored.getPhotoUrls()).containsExactly("listing/new.jpg");
        assertThat(stored.getInterestTag()).isNull();
        assertThat(stored.getStatus()).isEqualTo(ListingStatus.ACTIVE);
        assertThat(stored.getCatalogSetNumber()).isEqualTo("10307");
        assertThat(stored.getListedAt()).isEqualTo(T0);

        // 가격을 올리면 직전가 필드가 문서에서 빠진다.
        stored.revise(revision(12_000), T0.plusSeconds(20));
        assertThat(listings.updateIfActive(stored)).isTrue();
        Document raw = mongo.getCollection("listings")
                .find(new Document("_id", "revised"))
                .first();
        assertThat(raw).doesNotContainKey("previousPrice").doesNotContainKey("interestTag");
        assertThat(listings.findById("revised").orElseThrow().getPreviousPrice())
                .isNull();
    }

    private Object rawListedAt(String id) {
        return mongo.getCollection("listings")
                .find(new Document("_id", id))
                .first()
                .get("listedAt");
    }

    private static List<String> ids(List<Listing> found) {
        return found.stream().map(Listing::getId).toList();
    }

    private static ListingRevision revision(long price) {
        return new ListingRevision(
                "수정한 제목",
                "수정한 설명",
                Money.won(price),
                ItemCondition.LIKE_NEW,
                new ConditionDisclosure(Completeness.FULL_BOX, true, true, false, "", ""),
                List.of("listing/new.jpg"),
                null);
    }

    private static Listing listing(String id, String sellerId, Instant createdAt) {
        return Listing.create(
                id,
                sellerId,
                "원래 제목",
                "설명",
                Money.won(10_000),
                ItemCondition.USED_GOOD,
                ConditionDisclosure.basic(),
                List.of("listing/photo.jpg"),
                "10307",
                ListingCategory.SET,
                InterestTag.TECHNIC,
                createdAt);
    }

    private void insertLegacy(String id, Instant createdAt) {
        mongo.getDb()
                .getCollection("listings")
                .insertOne(new Document("_id", id)
                        .append("sellerId", "seller-1")
                        .append("title", "레거시 매물")
                        .append("description", "설명")
                        .append("priceAmount", 10_000L)
                        .append("priceCurrency", "KRW")
                        .append("condition", "USED_GOOD")
                        .append("photoUrls", List.of("listing/photo.jpg"))
                        .append("category", "SET")
                        .append("status", "ACTIVE")
                        .append("createdAt", Date.from(createdAt)));
    }
}
