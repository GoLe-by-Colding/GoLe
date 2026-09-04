package com.gole.api.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.chat.application.SupportNotificationOutboxProperties;
import com.gole.api.chat.application.SupportNotificationOutboxWorker;
import com.gole.api.chat.application.SupportOperationalEventNotifier;
import com.gole.api.chat.application.port.out.SupportNotificationOutboxPort;
import com.gole.api.chat.domain.model.SupportCategory;
import com.gole.api.chat.domain.model.SupportNotificationEvent;
import com.gole.api.chat.domain.model.SupportNotificationEvent.EventType;
import com.gole.api.chat.domain.model.SupportNotificationEvent.State;
import com.gole.api.chat.domain.model.SupportStatus;
import com.gole.api.chat.domain.model.SupportTicket;
import com.gole.api.common.operations.ConfirmedOperationalEventPublisher.DeliveryResult;
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
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class SupportNotificationOutboxIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-04T01:02:03Z");

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
        registry.add("gole.support-notification-outbox.terminal-retention", () -> "PT1H");
    }

    @Autowired
    SupportNotificationOutboxPort outbox;

    @Autowired
    SupportOperationalEventNotifier notifier;

    @Autowired
    MongoTemplate mongo;

    @Autowired
    PlatformTransactionManager transactions;

    @BeforeEach
    void clean() {
        mongo.getDb().getCollection("support_notification_outbox").deleteMany(new Document());
        mongo.getDb().getCollection("support_tickets").deleteMany(new Document());
    }

    @Test
    void inquiryRecordAndNonIdentifyingOutboxCommitOrRollbackTogether() {
        TransactionTemplate transaction = new TransactionTemplate(transactions);
        SupportTicket committed =
                SupportTicket.opened("private-room-commit", "private-requester-commit", SupportCategory.GENERAL, NOW);

        transaction.executeWithoutResult(ignored -> {
            mongo.insert(new Document("_id", committed.roomId()), "support_tickets");
            notifier.opened(committed);
        });

        assertThat(mongo.getCollection("support_tickets").countDocuments()).isEqualTo(1);
        Document stored =
                mongo.getCollection("support_notification_outbox").find().first();
        assertThat(stored).isNotNull();
        assertThat(stored.toJson())
                .doesNotContain(
                        "private-room-commit", "private-requester-commit", "roomId", "requesterId", "title", "content");

        SupportTicket rolledBack = SupportTicket.opened(
                "private-room-rollback", "private-requester-rollback", SupportCategory.PRIVACY_ACCESS, NOW);
        assertThatThrownBy(() -> transaction.executeWithoutResult(ignored -> {
                    mongo.insert(new Document("_id", rolledBack.roomId()), "support_tickets");
                    notifier.opened(rolledBack);
                    throw new IllegalStateException("force rollback");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(mongo.getCollection("support_tickets").countDocuments()).isEqualTo(1);
        assertThat(mongo.getCollection("support_notification_outbox").countDocuments())
                .isEqualTo(1);
    }

    @Test
    void discordFailureRunsAfterCommitAndCannotRollBackAcceptedInquiry() {
        TransactionTemplate transaction = new TransactionTemplate(transactions);
        SupportTicket ticket = SupportTicket.opened("private-room", "private-requester", SupportCategory.GENERAL, NOW);
        transaction.executeWithoutResult(ignored -> {
            mongo.insert(new Document("_id", ticket.roomId()), "support_tickets");
            notifier.opened(ticket);
        });

        SupportNotificationOutboxProperties properties = new SupportNotificationOutboxProperties();
        properties.setProcessingEnabled(true);
        properties.setBatchSize(1);
        properties.setMaximumAttempts(3);
        properties.setInitialBackoff(Duration.ofSeconds(10));
        SupportNotificationOutboxWorker worker = new SupportNotificationOutboxWorker(
                outbox,
                ignored -> DeliveryResult.retryable("HTTP_503", null),
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));

        worker.drain();

        assertThat(mongo.getCollection("support_tickets").countDocuments()).isEqualTo(1);
        Document receipt =
                mongo.getCollection("support_notification_outbox").find().first();
        assertThat(receipt).isNotNull();
        assertThat(receipt.getString("state")).isEqualTo(State.PENDING.name());
        assertThat(receipt.getInteger("attempts")).isEqualTo(1);
        assertThat(receipt.getString("lastErrorCode")).isEqualTo("HTTP_503");
    }

    @Test
    void terminalReceiptHasConfiguredExpiryAndMongoTtlIndex() {
        outbox.enqueue(pending("event-retained"));
        SupportNotificationEvent claimed =
                outbox.claimNext(NOW, Duration.ofSeconds(30), 3).orElseThrow();

        Instant deliveredAt = NOW.plusSeconds(5);
        outbox.delivered(claimed.eventId(), claimed.leaseToken(), deliveredAt);

        Document receipt = mongo.getCollection("support_notification_outbox")
                .find(new Document("_id", "event-retained"))
                .first();
        assertThat(receipt).isNotNull();
        assertThat(receipt.getDate("expiresAt")).isEqualTo(Date.from(deliveredAt.plus(Duration.ofHours(1))));
        assertThat(mongo.getCollection("support_notification_outbox").listIndexes())
                .anySatisfy(index -> {
                    assertThat(index.getString("name")).isEqualTo("support_notification_terminal_ttl");
                    assertThat(index.get("expireAfterSeconds", Number.class).longValue())
                            .isZero();
                });
    }

    @Test
    @Timeout(10)
    void competingWorkersIssueOneLeaseAndExpiredLeaseIsRecoverable() throws Exception {
        SupportNotificationEvent pending = pending("event-concurrent");
        outbox.enqueue(pending);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Optional<SupportNotificationEvent>> first = workers.submit(() -> claimAfter(ready, start));
            Future<Optional<SupportNotificationEvent>> second = workers.submit(() -> claimAfter(ready, start));
            ready.await();
            start.countDown();

            List<SupportNotificationEvent> claimed = List.of(first.get(), second.get()).stream()
                    .flatMap(Optional::stream)
                    .toList();
            assertThat(claimed).singleElement().satisfies(event -> {
                assertThat(event.attempts()).isEqualTo(1);
                assertThat(event.state()).isEqualTo(State.IN_FLIGHT);
            });

            SupportNotificationEvent firstLease = claimed.getFirst();
            assertThat(outbox.claimNext(NOW.plusSeconds(29), Duration.ofSeconds(30), 3))
                    .isEmpty();
            SupportNotificationEvent recovered = outbox.claimNext(NOW.plusSeconds(30), Duration.ofSeconds(30), 3)
                    .orElseThrow();
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
            workers.close();
        }
    }

    @Test
    void abandonedLastAttemptMovesToDeadLetterInsteadOfRemainingInFlight() {
        outbox.enqueue(pending("event-dead"));
        SupportNotificationEvent claimed =
                outbox.claimNext(NOW, Duration.ofSeconds(30), 1).orElseThrow();
        assertThat(claimed.attempts()).isEqualTo(1);

        assertThat(outbox.claimNext(NOW.plusSeconds(30), Duration.ofSeconds(30), 1))
                .isEmpty();

        SupportNotificationEvent dead = outbox.findById("event-dead").orElseThrow();
        assertThat(dead.state()).isEqualTo(State.DEAD_LETTER);
        assertThat(dead.lastErrorCode()).isEqualTo("LEASE_EXPIRED_AFTER_MAX_ATTEMPTS");
        assertThat(mongo.getCollection("support_notification_outbox")
                        .find(new Document("_id", "event-dead"))
                        .first()
                        .getDate("expiresAt"))
                .isEqualTo(Date.from(NOW.plusSeconds(30).plus(Duration.ofHours(1))));
    }

    @Test
    @Timeout(10)
    void concurrentManualRecoveryRequeuesDeadLetterOnlyOnceAndClearsTerminalFields() throws Exception {
        outbox.enqueue(pending("event-recovery"));
        outbox.claimNext(NOW, Duration.ofSeconds(30), 1).orElseThrow();
        outbox.claimNext(NOW.plusSeconds(30), Duration.ofSeconds(30), 1);

        ExecutorService operators = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Instant requeuedAt = NOW.plusSeconds(60);
        try {
            Future<Optional<SupportNotificationEvent>> first =
                    operators.submit(() -> requeueAfter(ready, start, requeuedAt));
            Future<Optional<SupportNotificationEvent>> second =
                    operators.submit(() -> requeueAfter(ready, start, requeuedAt));
            ready.await();
            start.countDown();

            assertThat(List.of(first.get(), second.get()).stream().flatMap(Optional::stream))
                    .singleElement()
                    .satisfies(requeued -> {
                        assertThat(requeued.state()).isEqualTo(State.PENDING);
                        assertThat(requeued.attempts()).isZero();
                        assertThat(requeued.nextAttemptAt()).isEqualTo(requeuedAt);
                    });
            Document stored = mongo.getCollection("support_notification_outbox")
                    .find(new Document("_id", "event-recovery"))
                    .first();
            assertThat(stored).isNotNull();
            assertThat(stored)
                    .doesNotContainKeys("lastErrorCode", "expiresAt", "leaseToken", "leaseUntil", "deliveredAt");
        } finally {
            start.countDown();
            operators.close();
        }
    }

    private Optional<SupportNotificationEvent> claimAfter(CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        start.await();
        return outbox.claimNext(NOW, Duration.ofSeconds(30), 3);
    }

    private Optional<SupportNotificationEvent> requeueAfter(
            CountDownLatch ready, CountDownLatch start, Instant requeuedAt) throws InterruptedException {
        ready.countDown();
        start.await();
        return outbox.requeueDeadLetter("event-recovery", requeuedAt);
    }

    private static SupportNotificationEvent pending(String eventId) {
        return new SupportNotificationEvent(
                eventId,
                EventType.OPENED,
                SupportCategory.GENERAL,
                SupportStatus.UNASSIGNED,
                State.PENDING,
                0,
                NOW,
                null,
                null,
                null,
                NOW,
                NOW,
                null);
    }
}
