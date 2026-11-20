package com.gole.api.listing.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.listing.application.query.ListingPageCursor;
import com.gole.api.listing.application.query.ListingSearchQuery;
import com.gole.api.listing.application.query.ListingSortOrder;
import com.gole.api.listing.domain.model.ConditionDisclosure;
import com.gole.api.listing.domain.model.ItemCondition;
import com.gole.api.listing.domain.model.Listing;
import com.gole.api.listing.domain.model.ListingCategory;
import com.gole.api.listing.domain.model.ListingStatus;
import com.gole.api.listing.domain.model.Money;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 매물 검색 페이지의 실제 Mongo 왕복. (listing-search-paging P3·P4)
 *
 * <p>정렬 키가 겹치는 매물을 넣고 작은 페이지로 끝까지 넘겼을 때, 빠지거나 겹치지 않고 한 번에 읽은 순서와 같아야 한다.
 */
@Testcontainers
class ListingSearchPagingIntegrationTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    private static final Instant T1 = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-11-02T00:00:00Z");

    private static MongoClient client;
    private static ListingPersistenceAdapter adapter;
    private static List<Listing> stored;

    @BeforeAll
    static void seed() {
        client = MongoClients.create(MONGO.getReplicaSetUrl());
        MongoTemplate template = new MongoTemplate(client, "paging");
        ListingMongoRepository repository =
                new MongoRepositoryFactory(template).getRepository(ListingMongoRepository.class);
        adapter = new ListingPersistenceAdapter(repository, template);
        stored = new ArrayList<>();
        // 같은 시각 셋(T2), 같은 가격 셋(30,000)을 섞어 동점 처리를 드러낸다.
        stored.add(adapter.save(listing("a", 30_000, T2, ListingStatus.ACTIVE)));
        stored.add(adapter.save(listing("b", 30_000, T2, ListingStatus.ACTIVE)));
        stored.add(adapter.save(listing("c", 30_000, T1, ListingStatus.ACTIVE)));
        stored.add(adapter.save(listing("d", 10_000, T2, ListingStatus.ACTIVE)));
        stored.add(adapter.save(listing("e", 50_000, T1, ListingStatus.ACTIVE)));
        stored.add(adapter.save(listing("f", 20_000, T1, ListingStatus.ACTIVE)));
        stored.add(adapter.save(listing("sold", 15_000, T2, ListingStatus.SOLD)));
    }

    @AfterAll
    static void close() {
        client.close();
    }

    @ParameterizedTest
    @EnumSource(ListingSortOrder.class)
    void pagingThroughSmallPagesMatchesOneOrderedReadWithoutGapsOrRepeats(ListingSortOrder sort) {
        ListingSearchQuery query = new ListingSearchQuery(null, null, null, null, sort, null);
        List<String> paged = new ArrayList<>();
        ListingPageCursor after = null;
        for (int guard = 0; guard < 10; guard++) {
            List<Listing> page = adapter.searchPage(query, after, 2);
            page.forEach(listing -> paged.add(listing.getId()));
            if (page.size() < 2) {
                break;
            }
            after = ListingPageCursor.after(page.getLast(), sort);
        }

        assertThat(paged).containsExactlyElementsOf(expectedOrder(sort));
    }

    /**
     * 끌올 도입 전 문서는 {@code listedAt}이 저장돼 있지 않다. 도메인은 {@code createdAt}으로 대신 보지만 Mongo 정렬·커서는
     * 저장값을 보므로, 시작할 때 도는 백필이 채운 뒤에는 최신순 페이지에서 빠지지 않아야 한다.
     */
    @org.junit.jupiter.api.Test
    void legacyListingWithoutStoredListedAtIsPagedAfterBackfill() {
        MongoTemplate template = new MongoTemplate(client, "paging-legacy");
        ListingPersistenceAdapter legacyAdapter = new ListingPersistenceAdapter(
                new MongoRepositoryFactory(template).getRepository(ListingMongoRepository.class), template);
        legacyAdapter.save(listing("new-1", 10_000, T2, ListingStatus.ACTIVE));
        legacyAdapter.save(listing("new-2", 10_000, T2, ListingStatus.ACTIVE));
        legacyAdapter.save(listing("legacy", 10_000, T1, ListingStatus.ACTIVE));
        template.getCollection("listings")
                .updateOne(
                        new org.bson.Document("_id", "paging-legacy"),
                        new org.bson.Document("$unset", new org.bson.Document("listedAt", "")));

        new ListingListedAtBackfill(template).backfill();

        ListingSearchQuery newest = new ListingSearchQuery(null, null, null, null, ListingSortOrder.NEWEST, null);
        List<String> paged = new ArrayList<>();
        ListingPageCursor after = null;
        for (int guard = 0; guard < 5; guard++) {
            List<Listing> page = legacyAdapter.searchPage(newest, after, 2);
            page.forEach(listing -> paged.add(listing.getId()));
            if (page.size() < 2) {
                break;
            }
            after = ListingPageCursor.after(page.getLast(), ListingSortOrder.NEWEST);
        }
        assertThat(paged).containsExactly("paging-new-2", "paging-new-1", "paging-legacy");
    }

    private static List<String> expectedOrder(ListingSortOrder sort) {
        Comparator<Listing> byId = Comparator.comparing(Listing::getId);
        Comparator<Listing> order =
                switch (sort) {
                    case NEWEST ->
                        Comparator.comparing(Listing::getListedAt)
                                .thenComparing(byId)
                                .reversed();
                    case PRICE_ASC ->
                        Comparator.comparingLong((Listing l) -> l.getPrice().amount())
                                .thenComparing(byId);
                    case PRICE_DESC ->
                        Comparator.comparingLong((Listing l) -> l.getPrice().amount())
                                .thenComparing(byId)
                                .reversed();
                };
        return stored.stream()
                .filter(Listing::isActive)
                .sorted(order)
                .map(Listing::getId)
                .toList();
    }

    private static Listing listing(String id, long price, Instant listedAt, ListingStatus status) {
        return new Listing(
                "paging-" + id,
                "seller-1",
                "매물 " + id,
                "",
                Money.won(price),
                ItemCondition.USED_GOOD,
                ConditionDisclosure.basic(),
                List.of("photo-" + id + ".jpg"),
                null,
                ListingCategory.SET,
                null,
                status,
                listedAt,
                listedAt,
                null,
                null,
                null);
    }
}
