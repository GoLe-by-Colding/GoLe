package com.gole.api.offer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.common.exception.ConflictException;
import com.gole.api.offer.application.port.out.OfferRepositoryPort;
import com.gole.api.offer.domain.exception.OfferErrors;
import com.gole.api.offer.domain.model.OfferStatus;
import com.gole.api.offer.domain.model.PriceOffer;
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
 * 제안 상태 전이의 원자성과 대기 제안 유일성을 실제 MongoDB로 확인한다. (price-offer O5, O11, B5)
 */
@SpringBootTest
@Testcontainers
class OfferPersistenceIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-03-11T00:00:00.123456Z");
    private static final Duration PENDING_TTL = Duration.ofHours(48);
    private static final Duration ACCEPTED_TTL = Duration.ofHours(72);

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
    OfferRepositoryPort offers;

    @Autowired
    MongoTemplate mongo;

    @BeforeEach
    void clean() {
        mongo.getDb().getCollection("offers").deleteMany(new Document());
    }

    private static PriceOffer pending(String id, String listingId, String buyerId) {
        return PriceOffer.propose(id, listingId, "room-1", buyerId, "seller-1", 250_000, 280_000, T0, PENDING_TTL);
    }

    @Test
    void concurrentAcceptAndWithdrawLetExactlyOneWin() throws Exception {
        for (int round = 0; round < 10; round++) {
            String id = "offer-" + round;
            PriceOffer stored = offers.insert(pending(id, "listing-" + round, "buyer-1"));
            Instant later = T0.plusSeconds(60);

            List<Callable<Optional<PriceOffer>>> contenders = List.of(
                    () -> offers.transition(stored, stored.accept(later, ACCEPTED_TTL)),
                    () -> offers.transition(stored, stored.withdraw(later)),
                    () -> offers.transition(stored, stored.decline(later)));
            List<Optional<PriceOffer>> results = raceAll(contenders);

            assertThat(results.stream().filter(Optional::isPresent).count())
                    .as("round %d: 같은 원본에서 출발한 전이는 하나만 이긴다", round)
                    .isEqualTo(1);
            OfferStatus winner = results.stream()
                    .flatMap(Optional::stream)
                    .findFirst()
                    .orElseThrow()
                    .status();
            assertThat(offers.findById(id).orElseThrow().status()).isEqualTo(winner);
        }
    }

    @Test
    void acceptedOfferStoresNewExpiryAndCannotBeAcceptedTwice() {
        PriceOffer stored = offers.insert(pending("offer-1", "listing-1", "buyer-1"));
        Instant acceptedAt = T0.plusSeconds(3_600);

        PriceOffer accepted = offers.transition(stored, stored.accept(acceptedAt, ACCEPTED_TTL))
                .orElseThrow();

        assertThat(accepted.status()).isEqualTo(OfferStatus.ACCEPTED);
        PriceOffer reread = offers.findById("offer-1").orElseThrow();
        assertThat(reread.expiresAt()).isEqualTo(accepted.expiresAt());
        // 이미 전이된 뒤 옛 원본으로 다시 시도하면 조건이 맞지 않는다.
        assertThat(offers.transition(stored, stored.accept(acceptedAt, ACCEPTED_TTL)))
                .isEmpty();
        // 다시 읽은 최신 상태에서 출발하면 철회는 된다(O9 — 수락된 제안도 구매자가 거둘 수 있다).
        assertThat(offers.transition(reread, reread.withdraw(acceptedAt.plusSeconds(1))))
                .get()
                .extracting(PriceOffer::status)
                .isEqualTo(OfferStatus.WITHDRAWN);
    }

    @Test
    void secondPendingForSameListingAndBuyerIsRejectedEvenUnderRace() throws Exception {
        List<Callable<Optional<PriceOffer>>> contenders = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String id = "offer-race-" + i;
            contenders.add(() -> {
                try {
                    return Optional.of(offers.insert(pending(id, "listing-1", "buyer-1")));
                } catch (ConflictException alreadyPending) {
                    assertThat(alreadyPending.getCode()).isEqualTo(OfferErrors.ALREADY_PENDING);
                    return Optional.empty();
                }
            });
        }

        List<Optional<PriceOffer>> results = raceAll(contenders);

        assertThat(results.stream().filter(Optional::isPresent).count()).isEqualTo(1);
        assertThat(mongo.getDb().getCollection("offers").countDocuments(new Document("status", "PENDING")))
                .isEqualTo(1);
    }

    @Test
    void stalePendingIsExpiredToFreeTheSlotAndOtherBuyersAreIndependent() {
        offers.insert(pending("offer-old", "listing-1", "buyer-1"));
        offers.insert(pending("offer-other", "listing-1", "buyer-2"));
        Instant afterTtl = T0.plus(PENDING_TTL).plusSeconds(1);

        assertThat(offers.existsValidPending("listing-1", "buyer-1", afterTtl)).isFalse();
        assertThat(offers.expireStalePending("listing-1", "buyer-1", afterTtl)).isEqualTo(1);

        PriceOffer fresh = PriceOffer.propose(
                "offer-new", "listing-1", "room-1", "buyer-1", "seller-1", 240_000, 280_000, afterTtl, PENDING_TTL);
        offers.insert(fresh);

        assertThat(offers.findById("offer-old").orElseThrow().status()).isEqualTo(OfferStatus.EXPIRED);
        assertThat(offers.existsValidPending("listing-1", "buyer-1", afterTtl)).isTrue();
        assertThat(offers.findById("offer-other").orElseThrow().status()).isEqualTo(OfferStatus.PENDING);
        assertThatThrownBy(() -> offers.insert(PriceOffer.propose(
                        "offer-dup",
                        "listing-1",
                        "room-1",
                        "buyer-1",
                        "seller-1",
                        230_000,
                        280_000,
                        afterTtl,
                        PENDING_TTL)))
                .isInstanceOf(ConflictException.class);
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
