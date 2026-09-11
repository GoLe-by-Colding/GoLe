package com.gole.api.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.notification.application.port.out.AlimtalkSenderPort;
import com.gole.api.notification.application.port.out.InterestTagAlimtalkOutboxPort;
import com.gole.api.notification.application.port.out.InterestTagRecipientPort;
import com.gole.api.notification.application.service.InterestTagAlimtalkOutboxWorker;
import com.gole.api.notification.application.service.InterestTagAlimtalkProperties;
import com.gole.api.notification.domain.model.InterestTagAlimtalkEvent;
import com.gole.api.notification.domain.model.InterestTagAlimtalkEvent.State;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class InterestTagAlimtalkOutboxIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-11T01:02:03Z");
    private static final Duration LEASE = Duration.ofSeconds(30);
    private static final String COLLECTION = "interest_tag_alimtalk_outbox";

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
        registry.add("gole.interest-tag-alimtalk.enabled", () -> "false");
        registry.add("gole.interest-tag-alimtalk.terminal-retention", () -> "PT1H");
    }

    @Autowired
    InterestTagAlimtalkOutboxPort outbox;

    @Autowired
    MongoTemplate mongo;

    @BeforeEach
    void clean() {
        mongo.getDb().getCollection(COLLECTION).deleteMany(new Document());
    }

    @Test
    void deterministicDeliveryIdIgnoresDuplicatesAndRawDocumentHasNoPhoneNumber() {
        InterestTagAlimtalkEvent delivery = delivery("listing-duplicate", "recipient-duplicate");

        outbox.enqueue(delivery);
        outbox.enqueue(delivery);

        assertThat(mongo.getCollection(COLLECTION).countDocuments()).isEqualTo(1);
        Document stored = stored(delivery.eventId());
        assertThat(stored.getString("_id")).isEqualTo("listing-duplicate:recipient-duplicate");
        assertThat(stored.getString("recipientAccountId")).isEqualTo("recipient-duplicate");
        assertThat(stored).doesNotContainKey("phoneNumber");
        assertThat(stored.toJson()).doesNotContain("phoneNumber");
    }

    @Test
    @Timeout(10)
    void competingClaimsIssueOneLeaseAndExpiredLeaseCanBeReclaimed() throws Exception {
        InterestTagAlimtalkEvent pending = delivery("listing-lease", "recipient-lease");
        outbox.enqueue(pending);
        ExecutorService claimers = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Optional<InterestTagAlimtalkEvent>> first = claimers.submit(() -> claimAfter(ready, start, NOW, 3));
            Future<Optional<InterestTagAlimtalkEvent>> second = claimers.submit(() -> claimAfter(ready, start, NOW, 3));
            ready.await();
            start.countDown();

            List<InterestTagAlimtalkEvent> claimed = List.of(first.get(), second.get()).stream()
                    .flatMap(Optional::stream)
                    .toList();
            assertThat(claimed).singleElement().satisfies(event -> {
                assertThat(event.state()).isEqualTo(State.IN_FLIGHT);
                assertThat(event.attempts()).isEqualTo(1);
            });

            InterestTagAlimtalkEvent firstLease = claimed.getFirst();
            assertThat(outbox.claimNext(NOW.plusSeconds(29), LEASE, 3)).isEmpty();
            InterestTagAlimtalkEvent recovered =
                    outbox.claimNext(NOW.plusSeconds(30), LEASE, 3).orElseThrow();
            assertThat(recovered.attempts()).isEqualTo(2);
            assertThat(recovered.leaseToken()).isNotEqualTo(firstLease.leaseToken());

            outbox.delivered(recovered.eventId(), firstLease.leaseToken(), NOW.plusSeconds(31));
            assertThat(outbox.findById(recovered.eventId()).orElseThrow().state())
                    .isEqualTo(State.IN_FLIGHT);
            outbox.delivered(recovered.eventId(), recovered.leaseToken(), NOW.plusSeconds(31));
            assertThat(outbox.findById(recovered.eventId()).orElseThrow().state())
                    .isEqualTo(State.DELIVERED);
        } finally {
            start.countDown();
            claimers.close();
        }
    }

    @Test
    void expiredLeaseAtMaximumAttemptsMovesToDeadLetter() {
        InterestTagAlimtalkEvent pending = delivery("listing-dead", "recipient-dead");
        outbox.enqueue(pending);
        InterestTagAlimtalkEvent claimed = outbox.claimNext(NOW, LEASE, 1).orElseThrow();
        assertThat(claimed.attempts()).isEqualTo(1);

        assertThat(outbox.claimNext(NOW.plus(LEASE), LEASE, 1)).isEmpty();

        InterestTagAlimtalkEvent dead = outbox.findById(pending.eventId()).orElseThrow();
        assertThat(dead.state()).isEqualTo(State.DEAD_LETTER);
        assertThat(dead.lastErrorCode()).isEqualTo("LEASE_EXPIRED_AFTER_MAX_ATTEMPTS");
        assertThat(stored(pending.eventId()).getDate("expiresAt"))
                .isEqualTo(Date.from(NOW.plus(LEASE).plus(Duration.ofHours(1))));
    }

    @Test
    void continueFanoutPreservesAttemptsAndLeavesNoTerminalExpiry() {
        InterestTagAlimtalkEvent fanout = fanout("listing-continue");
        outbox.enqueue(fanout);
        InterestTagAlimtalkEvent claimed = outbox.claimNext(NOW, LEASE, 3).orElseThrow();
        assertThat(claimed.attempts()).isEqualTo(1);

        Instant continuedAt = NOW.plusSeconds(5);
        outbox.continueFanout(claimed.eventId(), claimed.leaseToken(), "recipient-200", 200, continuedAt);

        InterestTagAlimtalkEvent continued = outbox.findById(fanout.eventId()).orElseThrow();
        assertThat(continued.state()).isEqualTo(State.PENDING);
        assertThat(continued.attempts()).isZero();
        assertThat(continued.cursorAccountId()).isEqualTo("recipient-200");
        assertThat(continued.enqueuedRecipients()).isEqualTo(200);
        assertThat(continued.nextAttemptAt()).isEqualTo(continuedAt);
        assertThat(stored(fanout.eventId())).doesNotContainKeys("leaseToken", "leaseUntil", "completedAt", "expiresAt");
    }

    @Test
    void expiresAtExistsOnlyAfterTerminalTransitionAndTtlIndexIsConfigured() {
        InterestTagAlimtalkEvent delivery = delivery("listing-retained", "recipient-retained");
        outbox.enqueue(delivery);
        assertThat(stored(delivery.eventId())).doesNotContainKey("expiresAt");

        InterestTagAlimtalkEvent claimed = outbox.claimNext(NOW, LEASE, 3).orElseThrow();
        assertThat(stored(delivery.eventId())).doesNotContainKey("expiresAt");

        Instant completedAt = NOW.plusSeconds(5);
        outbox.delivered(claimed.eventId(), claimed.leaseToken(), completedAt);

        assertThat(stored(delivery.eventId()).getDate("expiresAt"))
                .isEqualTo(Date.from(completedAt.plus(Duration.ofHours(1))));
        assertThat(mongo.getCollection(COLLECTION).listIndexes()).anySatisfy(index -> {
            assertThat(index.getString("name")).isEqualTo("itat_terminal_ttl");
            assertThat(index.get("expireAfterSeconds", Number.class).longValue())
                    .isZero();
        });
    }

    @Test
    @Timeout(10)
    void competingWorkersSendOneDeliveryOnlyOnce() throws Exception {
        InterestTagAlimtalkEvent delivery = delivery("listing-worker", "recipient-worker");
        outbox.enqueue(delivery);
        AtomicInteger sends = new AtomicInteger();
        CountDownLatch sendEntered = new CountDownLatch(1);
        CountDownLatch releaseSend = new CountDownLatch(1);
        AlimtalkSenderPort sender = command -> {
            sends.incrementAndGet();
            sendEntered.countDown();
            await(releaseSend);
            return new AlimtalkSenderPort.AlimtalkAcceptance("test-group", "test-message", "2000", "accepted");
        };
        InterestTagAlimtalkOutboxWorker firstWorker = worker(sender);
        InterestTagAlimtalkOutboxWorker secondWorker = worker(sender);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = workers.submit(firstWorker::drain);
            sendEntered.await();
            Future<?> second = workers.submit(secondWorker::drain);
            second.get();

            assertThat(sends).hasValue(1);
            assertThat(outbox.findById(delivery.eventId()).orElseThrow().state())
                    .isEqualTo(State.IN_FLIGHT);

            releaseSend.countDown();
            first.get();
            assertThat(sends).hasValue(1);
            assertThat(outbox.findById(delivery.eventId()).orElseThrow().state())
                    .isEqualTo(State.DELIVERED);
        } finally {
            releaseSend.countDown();
            workers.close();
        }
    }

    private Optional<InterestTagAlimtalkEvent> claimAfter(
            CountDownLatch ready, CountDownLatch start, Instant now, int maximumAttempts) throws InterruptedException {
        ready.countDown();
        start.await();
        return outbox.claimNext(now, LEASE, maximumAttempts);
    }

    private InterestTagAlimtalkOutboxWorker worker(AlimtalkSenderPort sender) {
        InterestTagAlimtalkProperties properties = new InterestTagAlimtalkProperties();
        properties.setEnabled(true);
        properties.setTemplateId("TEST_TEMPLATE");
        properties.setBatchSize(1);
        properties.setMaximumAttempts(3);
        properties.setLeaseDuration(LEASE);
        return new InterestTagAlimtalkOutboxWorker(
                outbox,
                new InterestTagRecipientPort() {
                    @Override
                    public List<String> listEligibleAccountIds(String tagKey, String afterAccountId, int limit) {
                        return List.of();
                    }

                    @Override
                    public Optional<Recipient> resolveEligible(String accountId, String tagKey) {
                        return Optional.of(new Recipient(accountId, "TEST_PHONE_NUMBER"));
                    }
                },
                listingId -> Optional.of(
                        new com.gole.api.notification.application.port.out.ListingSnapshotPort.ListingSnapshot(
                                listingId, true)),
                (accountId, maximum, window) -> true,
                Optional.of(sender),
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Document stored(String eventId) {
        Document stored = mongo.getCollection(COLLECTION)
                .find(new Document("_id", eventId))
                .first();
        assertThat(stored).isNotNull();
        return stored;
    }

    private static InterestTagAlimtalkEvent fanout(String listingId) {
        return InterestTagAlimtalkEvent.fanout(listingId, "seller-test", "technic", "테크닉", "테스트 매물", NOW, NOW);
    }

    private static InterestTagAlimtalkEvent delivery(String listingId, String recipientAccountId) {
        return InterestTagAlimtalkEvent.delivery(fanout(listingId), recipientAccountId, NOW);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }
}
