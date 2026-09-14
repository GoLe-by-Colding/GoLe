package com.gole.api.brickfilter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gole.api.brickfilter.adapter.out.persistence.*;
import com.gole.api.brickfilter.application.port.out.*;
import com.gole.api.brickfilter.application.service.*;
import com.gole.api.brickfilter.domain.model.BrickJob;
import com.gole.api.brickfilter.domain.model.BrickJob.*;
import com.gole.api.common.exception.*;
import com.mongodb.client.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
class BrickFilterIntegrationTest {
    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    MongoClient client;
    MongoTemplate mongo;
    MongoBrickLedger ledger;
    MongoBrickBlobs blobs;
    BrickGeneratorPort generator;
    BrickFilterService service;
    MutableClock clock;
    byte[] image = {1, 2, 3};

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-09-08T03:00:00Z");

        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        public Instant instant() {
            return now;
        }

        void advance(long seconds) {
            now = now.plusSeconds(seconds);
        }
    }

    @BeforeEach
    void setup() {
        client = MongoClients.create(MONGO.getReplicaSetUrl());
        mongo = new MongoTemplate(
                client, "brick_test_" + UUID.randomUUID().toString().replace("-", ""));
        ledger = new MongoBrickLedger(mongo);
        blobs = new MongoBrickBlobs(mongo);
        clock = new MutableClock();
        generator = mock(BrickGeneratorPort.class);
        when(generator.enabled()).thenReturn(true);
        when(generator.generate(any(), any())).thenReturn(image);
        BrickImagePort images = mock(BrickImagePort.class);
        when(images.sanitize(any(), anyBoolean())).thenAnswer(invocation -> invocation.getArgument(0));
        BrickProviderGatePort gate = mock(BrickProviderGatePort.class);
        when(gate.acquire(any(), any())).thenReturn(true);
        service = new BrickFilterService(ledger, blobs, images, generator, gate, clock);
    }

    @AfterEach
    void close() {
        mongo.getDb().drop();
        client.close();
    }

    String id() {
        return "2026-09-08_" + UUID.randomUUID();
    }

    @Test
    void twoModesShareExactlyThreeAtomicReservations() throws Exception {
        try (var pool = Executors.newFixedThreadPool(12)) {
            var start = new CountDownLatch(1);
            var results = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 12; i++) {
                final var mode = i % 2 == 0 ? Mode.MINIFIGURE : Mode.BRICK_OBJECT;
                results.add(pool.submit(() -> {
                    start.await();
                    return ledger.reserve(
                            "u",
                            "2026-09-08",
                            new BrickJob(
                                    id(),
                                    mode,
                                    "digest",
                                    Status.RESERVED,
                                    clock.instant().plusSeconds(300),
                                    clock.instant().plusSeconds(86400)));
                }));
            }
            start.countDown();
            int successes = 0;
            for (var r : results) if (r.get(10, TimeUnit.SECONDS)) successes++;
            assertThat(successes).isEqualTo(3);
            assertThat(ledger.load("u", "2026-09-08").occupied()).isEqualTo(3);
        }
    }

    @Test
    void repeatedIdInvokesProviderOnceAndDifferentPayloadConflicts() {
        String id = id();
        var first = service.submit("a", id, Mode.MINIFIGURE, image);
        var second = service.submit("a", id, Mode.MINIFIGURE, image);
        assertThat(first.status()).isEqualTo(Status.SUCCEEDED);
        assertThat(second).isEqualTo(first);
        verify(generator, times(1)).generate(any(), any());
        assertThatThrownBy(() -> service.submit("a", id, Mode.BRICK_OBJECT, image))
                .isInstanceOf(ConflictException.class);
        assertThat(service.quota("a").remaining()).isEqualTo(2);
        assertThat(blobs.get("a", id, "source", clock.instant())).isEmpty();
        assertThat(service.result("a", id)).containsExactly(image);
        assertThatThrownBy(() -> service.result("b", id)).isInstanceOf(NotFoundException.class);
        assertThat(service.recent("b")).isEmpty();
    }

    @Test
    void concurrentSameIdHasOnlyOneReservation() throws Exception {
        String id = id();
        var job = new BrickJob(
                id,
                Mode.MINIFIGURE,
                "d",
                Status.RESERVED,
                clock.instant().plusSeconds(300),
                clock.instant().plusSeconds(86400));
        try (var pool = Executors.newFixedThreadPool(8)) {
            var results = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 8; i++) results.add(pool.submit(() -> ledger.reserve("u", "2026-09-08", job)));
            int n = 0;
            for (var r : results) if (r.get()) n++;
            assertThat(n).isEqualTo(1);
        }
        assertThat(ledger.load("u", "2026-09-08").occupied()).isEqualTo(1);
    }

    @Test
    void failureRefundsOnceAndNeverRetriesSameKey() {
        when(generator.generate(any(), any())).thenThrow(new RuntimeException("provider failure"));
        String id = id();
        assertThat(service.submit("u", id, Mode.BRICK_OBJECT, image).status()).isEqualTo(Status.FAILED);
        assertThat(service.submit("u", id, Mode.BRICK_OBJECT, image).status()).isEqualTo(Status.FAILED);
        assertThat(service.quota("u").remaining()).isEqualTo(3);
        verify(generator, times(1)).generate(any(), any());
        assertThat(blobs.get("u", id, "source", clock.instant())).isEmpty();
    }

    @Test
    void lateProviderSuccessCannotCommitAfterLeaseExpiry() {
        when(generator.generate(any(), any())).thenAnswer(invocation -> {
            clock.advance(301);
            return image;
        });
        String id = id();
        assertThat(service.submit("u", id, Mode.MINIFIGURE, image).status()).isEqualTo(Status.FAILED);
        assertThat(service.quota("u").remaining()).isEqualTo(3);
        assertThat(blobs.get("u", id, "result", clock.instant())).isEmpty();
    }

    @Test
    void crashRecoveryRefundsAndPurgesUncommittedSourceAndResult() {
        String id = id();
        ledger.reserve(
                "u",
                "2026-09-08",
                new BrickJob(
                        id,
                        Mode.MINIFIGURE,
                        "d",
                        Status.RESERVED,
                        clock.instant().plusSeconds(300),
                        clock.instant().plusSeconds(86400)));
        blobs.put("u", id, "source", image, clock.instant().plusSeconds(300));
        blobs.put("u", id, "result", image, clock.instant().plusSeconds(86400));
        clock.advance(301);
        service.cleanup();
        service.cleanup();
        assertThat(service.get("u", id).status()).isEqualTo(Status.FAILED);
        assertThat(service.quota("u").remaining()).isEqualTo(3);
        assertThat(mongo.getCollection("brick_filter_blobs").countDocuments()).isZero();
        verify(generator, never()).generate(any(), any());
    }

    @Test
    void seoulMidnightResetAndOldKeyReplayAndExpiry() {
        clock.now = Instant.parse("2026-09-08T14:59:59Z");
        String id = id();
        service.submit("u", id, Mode.MINIFIGURE, image);
        assertThat(service.quota("u").day()).isEqualTo("2026-09-08");
        clock.advance(2);
        assertThat(service.quota("u").day()).isEqualTo("2026-09-09");
        assertThat(service.quota("u").remaining()).isEqualTo(3);
        assertThat(service.submit("u", id, Mode.MINIFIGURE, image).status()).isEqualTo(Status.SUCCEEDED);
        assertThatThrownBy(() -> service.submit("u", id(), Mode.MINIFIGURE, image))
                .isInstanceOf(BadRequestException.class);
        clock.advance(86400);
        assertThatThrownBy(() -> service.result("u", id)).isInstanceOf(NotFoundException.class);
        service.cleanup();
        assertThat(mongo.getCollection("brick_filter_blobs").countDocuments()).isZero();
    }

    @Test
    void distributedProviderGateCapsConcurrencyAndCalls() throws Exception {
        var gate = new MongoBrickProviderGate(mongo);
        var tokens = new ArrayList<String>();
        try (var pool = Executors.newFixedThreadPool(10)) {
            var results = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 10; i++) {
                String token = UUID.randomUUID().toString();
                tokens.add(token);
                results.add(pool.submit(() -> gate.acquire(token, clock.instant())));
            }
            int n = 0;
            for (var r : results) if (r.get()) n++;
            assertThat(n).isEqualTo(2);
        }
        tokens.forEach(gate::release);
        for (int i = 0; i < 4; i++) {
            assertThat(gate.acquire("next" + i, clock.instant())).isTrue();
            gate.release("next" + i);
        }
        assertThat(gate.acquire("over", clock.instant())).isFalse();
        clock.advance(61);
        assertThat(gate.acquire("next-minute", clock.instant())).isTrue();
    }

    @Test
    void disabledNeverReservesOrCallsProvider() {
        when(generator.enabled()).thenReturn(false);
        assertThatThrownBy(() -> service.submit("u", id(), Mode.MINIFIGURE, image))
                .isInstanceOf(ServiceUnavailableException.class);
        assertThat(service.quota("u").remaining()).isEqualTo(3);
        verify(generator, never()).generate(any(), any());
    }

    @Test
    void lostCommitAcknowledgementPreservesSuccessfulResult() {
        var uncertain = spy(ledger);
        doAnswer(invocation -> {
                    ledger.complete(
                            invocation.getArgument(0),
                            invocation.getArgument(1),
                            invocation.getArgument(2),
                            invocation.getArgument(3));
                    throw new RuntimeException("lost acknowledgement");
                })
                .when(uncertain)
                .complete(any(), any(), any(), any());
        var images = mock(BrickImagePort.class);
        when(images.sanitize(any(), anyBoolean())).thenReturn(image);
        var gate = mock(BrickProviderGatePort.class);
        when(gate.acquire(any(), any())).thenReturn(true);
        var uncertainService = new BrickFilterService(uncertain, blobs, images, generator, gate, clock);
        String id = id();
        assertThat(uncertainService.submit("u", id, Mode.MINIFIGURE, image).status())
                .isEqualTo(Status.SUCCEEDED);
        assertThat(uncertainService.result("u", id)).containsExactly(image);
        assertThat(uncertainService.quota("u").remaining()).isEqualTo(2);
    }

    @Test
    void globalDailyBudgetIncludesFailuresAndLeaseCrashRecovery() {
        var gate = new MongoBrickProviderGate(mongo);
        assertThat(gate.acquire("crashed-a", clock.instant())).isTrue();
        assertThat(gate.acquire("crashed-b", clock.instant())).isTrue();
        clock.advance(301);
        for (int i = 2; i < 100; i++) {
            String token = "attempt-" + i;
            assertThat(gate.acquire(token, clock.instant())).isTrue();
            gate.release(token);
            clock.advance(61);
        }
        assertThat(gate.acquire("over-daily", clock.instant())).isFalse();
        clock.advance(86400);
        assertThat(gate.acquire("next-day", clock.instant())).isTrue();
    }
}
