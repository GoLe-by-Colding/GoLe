package com.gole.api.bid;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.bid.application.port.in.FillBidUseCase;
import com.gole.api.bid.application.port.in.FillBidUseCase.FillResult;
import com.gole.api.bid.application.port.in.PlaceBidUseCase;
import com.gole.api.bid.application.port.in.PlaceBidUseCase.PlaceBidCommand;
import com.gole.api.bid.application.port.out.BidRepositoryPort;
import com.gole.api.bid.domain.exception.BidErrors;
import com.gole.api.bid.domain.exception.DuplicateActiveBidException;
import com.gole.api.bid.domain.model.Bid;
import com.gole.api.bid.domain.model.BidCondition;
import com.gole.api.bid.domain.model.BidStatus;
import com.gole.api.catalog.application.port.out.CatalogAdminPort;
import com.gole.api.catalog.domain.model.LegoSet;
import com.gole.api.catalog.domain.model.RetirementStatus;
import com.gole.api.common.exception.ConflictException;
import com.gole.api.listing.application.port.out.ListingRepositoryPort;
import com.gole.api.listing.domain.model.ConditionDisclosure;
import com.gole.api.listing.domain.model.InterestTag;
import com.gole.api.listing.domain.model.ItemCondition;
import com.gole.api.listing.domain.model.Listing;
import com.gole.api.listing.domain.model.ListingCategory;
import com.gole.api.listing.domain.model.Money;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
 * 입찰 원장의 원자성과 판매자 즉시 판매의 단일 체결을 실제 MongoDB로 확인한다. (buy-bids D3, D7, B5)
 *
 * <p>체결은 실제 제안 컨텍스트까지 탄다 — 이긴 쪽만 수락 제안이 생겨야 한다.
 */
@SpringBootTest
@Testcontainers
class BidPersistenceIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-03-11T00:00:00.123456Z");

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
    BidRepositoryPort bids;

    @Autowired
    PlaceBidUseCase placeBid;

    @Autowired
    FillBidUseCase fillBid;

    @Autowired
    ListingRepositoryPort listings;

    @Autowired
    CatalogAdminPort catalogAdmin;

    @Autowired
    MongoTemplate mongo;

    @BeforeEach
    void clean() {
        for (String collection : List.of("bids", "offers", "listings", "notifications", "lego_sets")) {
            mongo.getDb().getCollection(collection).deleteMany(new Document());
        }
    }

    @Test
    void twoSellersFillingTheOnlyBidConcurrentlyCreateExactlyOneOffer() throws Exception {
        bids.insert(Bid.place("bid-1", "buyer-1", "10307", BidCondition.NEW_SEALED, 260_000, 30, Instant.now()));
        listings.save(listing("listing-a", "seller-a"));
        listings.save(listing("listing-b", "seller-b"));

        List<Callable<Optional<FillResult>>> sellers =
                List.of(() -> attemptFill("listing-a", "seller-a"), () -> attemptFill("listing-b", "seller-b"));
        List<Optional<FillResult>> results = raceAll(sellers);

        assertThat(results.stream().filter(Optional::isPresent).count()).isEqualTo(1);
        FillResult winner =
                results.stream().flatMap(Optional::stream).findFirst().orElseThrow();
        assertThat(winner.bidPrice()).isEqualTo(260_000);
        assertThat(mongo.getDb().getCollection("offers").countDocuments()).isEqualTo(1);
        Document offer = mongo.getDb().getCollection("offers").find().first();
        assertThat(offer.getString("buyerId")).isEqualTo("buyer-1");
        assertThat(offer.getString("status")).isEqualTo("ACCEPTED");
        assertThat(offer.getString("origin")).isEqualTo("BID");
        Bid stored = bids.findById("bid-1").orElseThrow();
        assertThat(stored.status()).isEqualTo(BidStatus.FILLED);
        assertThat(stored.offerId()).isEqualTo(winner.offerId());
        assertThat(stored.filledListingId()).isEqualTo(winner.listingId());
    }

    @Test
    void placingTheSameSlotConcurrentlyLeavesOneActiveBid() throws Exception {
        catalogAdmin.save(legoSet("10307"), false);
        List<Callable<Bid>> requests = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            long price = 200_000 + i * 1_000;
            requests.add(() -> placeBid.place(new PlaceBidCommand("buyer-1", "10307", "like_new", price, 30)));
        }

        List<Bid> placed = raceAll(requests);

        assertThat(placed).extracting(Bid::id).containsOnly(placed.getFirst().id());
        assertThat(mongo.getDb()
                        .getCollection("bids")
                        .countDocuments(new Document("status", "ACTIVE").append("bidderId", "buyer-1")))
                .isEqualTo(1);
    }

    @Test
    void activeSlotIsUniqueButExpiredBidCanBeReplacedByANewOne() {
        Bid first = bids.insert(Bid.place("bid-1", "buyer-1", "10307", BidCondition.USED_GOOD, 100_000, 7, T0));
        try {
            bids.insert(Bid.place("bid-2", "buyer-1", "10307", BidCondition.USED_GOOD, 110_000, 7, T0));
            throw new AssertionError("같은 자리의 두 번째 ACTIVE는 유일 인덱스에 걸려야 한다");
        } catch (DuplicateActiveBidException expected) {
            // 서비스가 갱신 경로로 다시 시도한다
        }

        Instant afterExpiry = T0.plus(Duration.ofDays(8));
        assertThat(bids.findActive("buyer-1", "10307", BidCondition.USED_GOOD, afterExpiry))
                .isEmpty();
        assertThat(bids.expireStale("buyer-1", "10307", BidCondition.USED_GOOD, afterExpiry))
                .isEqualTo(1);
        bids.insert(Bid.place("bid-3", "buyer-1", "10307", BidCondition.USED_GOOD, 120_000, 7, afterExpiry));

        assertThat(bids.findById(first.id()).orElseThrow().status()).isEqualTo(BidStatus.EXPIRED);
        assertThat(bids.findActive("buyer-1", "10307", BidCondition.USED_GOOD, afterExpiry))
                .get()
                .extracting(Bid::id)
                .isEqualTo("bid-3");
    }

    @Test
    void fillCandidatesAreBestPriceThenEarliestAndExcludeTheSeller() {
        bids.insert(Bid.place("seller-own", "seller-1", "10307", BidCondition.NEW_SEALED, 999_000, 30, T0));
        bids.insert(Bid.place("late", "buyer-late", "10307", BidCondition.NEW_SEALED, 260_000, 30, T0.plusSeconds(5)));
        bids.insert(Bid.place("early", "buyer-early", "10307", BidCondition.NEW_SEALED, 260_000, 30, T0));
        bids.insert(Bid.place("low", "buyer-low", "10307", BidCondition.NEW_SEALED, 200_000, 30, T0));
        bids.insert(Bid.place("other", "buyer-other", "10307", BidCondition.LIKE_NEW, 500_000, 30, T0));
        Instant now = T0.plusSeconds(60);

        List<Bid> candidates = bids.findFillCandidates("10307", BidCondition.NEW_SEALED, "seller-1", now, 3);
        List<String> matched = bids.findBiddersAtOrAbove("10307", BidCondition.NEW_SEALED, 250_000, now, 10);

        assertThat(candidates).extracting(Bid::id).containsExactly("early", "late", "low");
        assertThat(matched).containsExactlyInAnyOrder("seller-1", "buyer-early", "buyer-late");
        assertThat(bids.countActive("buyer-early", now)).isEqualTo(1);
        assertThat(bids.findActiveBySet("10307", now)).hasSize(5);
    }

    private Optional<FillResult> attemptFill(String listingId, String sellerId) {
        try {
            return Optional.of(fillBid.fill("10307", listingId, sellerId));
        } catch (ConflictException noBidLeft) {
            assertThat(noBidLeft.getCode()).isEqualTo(BidErrors.NOT_FOUND);
            return Optional.empty();
        }
    }

    private static Listing listing(String id, String sellerId) {
        return Listing.create(
                id,
                sellerId,
                "에펠탑 미개봉",
                "설명",
                Money.won(280_000),
                ItemCondition.NEW_SEALED,
                ConditionDisclosure.basic(),
                List.of("listing/photo.jpg"),
                "10307",
                ListingCategory.SET,
                InterestTag.ICONS,
                Instant.now());
    }

    private static LegoSet legoSet(String setNumber) {
        return new LegoSet(setNumber, "에펠탑", "Icons", 10_001, 2022, RetirementStatus.ACTIVE, null);
    }

    /** 모든 작업을 같은 출발선에 세운 뒤 한꺼번에 놓는다. */
    private static <T> List<T> raceAll(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
