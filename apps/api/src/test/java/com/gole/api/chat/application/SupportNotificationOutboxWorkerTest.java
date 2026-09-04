package com.gole.api.chat.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.chat.application.port.out.SupportNotificationOutboxPort;
import com.gole.api.chat.domain.model.SupportCategory;
import com.gole.api.chat.domain.model.SupportNotificationEvent;
import com.gole.api.chat.domain.model.SupportNotificationEvent.EventType;
import com.gole.api.chat.domain.model.SupportNotificationEvent.State;
import com.gole.api.chat.domain.model.SupportStatus;
import com.gole.api.common.operations.ConfirmedOperationalEventPublisher;
import com.gole.api.common.operations.ConfirmedOperationalEventPublisher.DeliveryResult;
import com.gole.api.common.operations.OperationalEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SupportNotificationOutboxWorkerTest {

    private static final Instant NOW = Instant.parse("2026-09-04T01:02:03Z");

    @Test
    void confirmedDeliveryCompletesReceiptAndPayloadHasEventIdButNoPersonalLink() {
        FakeOutbox outbox = new FakeOutbox();
        RecordingPublisher publisher = new RecordingPublisher(DeliveryResult.delivered());
        SupportNotificationOutboxWorker worker = worker(outbox, publisher, 3);
        SupportNotificationEvent event = claimed(1);

        worker.process(event);

        assertThat(outbox.delivered).containsExactly(event.eventId(), event.leaseToken());
        assertThat(publisher.events).singleElement().satisfies(delivery -> {
            assertThat(delivery.fields())
                    .containsEntry("이벤트 ID", "event-1")
                    .containsEntry("문의 유형", "PRIVACY_ACCESS")
                    .containsEntry("처리 상태", "IN_PROGRESS")
                    .containsEntry("관리자 경로", "/admin/support")
                    .doesNotContainKeys("문의 ID", "요청자", "이메일", "전화번호");
            assertThat(delivery.toString()).doesNotContain("room-1", "account-1", "private message");
        });
    }

    @Test
    void retryableFailureUsesExponentialBackoffWithoutLosingEvent() {
        FakeOutbox outbox = new FakeOutbox();
        SupportNotificationOutboxWorker worker =
                worker(outbox, new RecordingPublisher(DeliveryResult.retryable("HTTP_503", null)), 3);

        worker.process(claimed(2));

        assertThat(outbox.retry)
                .isEqualTo(new Retry("event-1", "lease-1", NOW, NOW.plusSeconds(20), "HTTP_503", false));
    }

    @Test
    void rateLimitDelayIsHonoredButClampedAndLastAttemptDeadLetters() {
        FakeOutbox outbox = new FakeOutbox();
        SupportNotificationOutboxWorker worker =
                worker(outbox, new RecordingPublisher(DeliveryResult.retryable("HTTP_429", Duration.ofHours(2))), 3);

        worker.process(claimed(3));

        assertThat(outbox.retry)
                .isEqualTo(new Retry("event-1", "lease-1", NOW, NOW.plus(Duration.ofHours(1)), "HTTP_429", true));
    }

    @Test
    void permanentClientFailureDeadLettersImmediately() {
        FakeOutbox outbox = new FakeOutbox();
        SupportNotificationOutboxWorker worker =
                worker(outbox, new RecordingPublisher(DeliveryResult.permanent("HTTP_400")), 12);

        worker.process(claimed(1));

        assertThat(outbox.retry.deadLetter()).isTrue();
        assertThat(outbox.retry.errorCode()).isEqualTo("HTTP_400");
    }

    @Test
    void disabledProcessorLeavesPendingEventsUnclaimed() {
        FakeOutbox outbox = new FakeOutbox();
        outbox.claims.add(claimed(1));
        SupportNotificationOutboxProperties properties = properties(3);
        properties.setProcessingEnabled(false);
        SupportNotificationOutboxWorker worker = new SupportNotificationOutboxWorker(
                outbox,
                new RecordingPublisher(DeliveryResult.delivered()),
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));

        worker.drain();

        assertThat(outbox.claimCalls).isZero();
        assertThat(outbox.delivered).isEmpty();
    }

    private static SupportNotificationOutboxWorker worker(
            FakeOutbox outbox, ConfirmedOperationalEventPublisher publisher, int maximumAttempts) {
        SupportNotificationOutboxProperties properties = properties(maximumAttempts);
        properties.setProcessingEnabled(true);
        return new SupportNotificationOutboxWorker(outbox, publisher, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static SupportNotificationOutboxProperties properties(int maximumAttempts) {
        SupportNotificationOutboxProperties properties = new SupportNotificationOutboxProperties();
        properties.setMaximumAttempts(maximumAttempts);
        properties.setInitialBackoff(Duration.ofSeconds(10));
        properties.setMaximumBackoff(Duration.ofHours(1));
        return properties;
    }

    private static SupportNotificationEvent claimed(int attempts) {
        return new SupportNotificationEvent(
                "event-1",
                EventType.REQUESTER_REPLIED,
                SupportCategory.PRIVACY_ACCESS,
                SupportStatus.IN_PROGRESS,
                State.IN_FLIGHT,
                attempts,
                NOW,
                "lease-1",
                NOW.plusSeconds(30),
                null,
                NOW.minusSeconds(1),
                NOW.minusSeconds(1),
                null);
    }

    private static final class RecordingPublisher implements ConfirmedOperationalEventPublisher {

        private final DeliveryResult result;
        private final List<OperationalEvent> events = new ArrayList<>();

        private RecordingPublisher(DeliveryResult result) {
            this.result = result;
        }

        @Override
        public DeliveryResult publishAndConfirm(OperationalEvent event) {
            events.add(event);
            return result;
        }
    }

    private static final class FakeOutbox implements SupportNotificationOutboxPort {

        private final ArrayDeque<SupportNotificationEvent> claims = new ArrayDeque<>();
        private int claimCalls;
        private List<String> delivered = List.of();
        private Retry retry;

        @Override
        public void enqueue(SupportNotificationEvent event) {
            claims.add(event);
        }

        @Override
        public Optional<SupportNotificationEvent> claimNext(Instant now, Duration leaseDuration, int maximumAttempts) {
            claimCalls++;
            return Optional.ofNullable(claims.poll());
        }

        @Override
        public void delivered(String eventId, String leaseToken, Instant deliveredAt) {
            delivered = List.of(eventId, leaseToken);
        }

        @Override
        public void retry(
                String eventId,
                String leaseToken,
                Instant failedAt,
                Instant nextAttemptAt,
                String errorCode,
                boolean deadLetter) {
            retry = new Retry(eventId, leaseToken, failedAt, nextAttemptAt, errorCode, deadLetter);
        }

        @Override
        public Optional<SupportNotificationEvent> requeueDeadLetter(String eventId, Instant requeuedAt) {
            return Optional.empty();
        }

        @Override
        public Optional<SupportNotificationEvent> findById(String eventId) {
            return Optional.empty();
        }
    }

    private record Retry(
            String eventId,
            String leaseToken,
            Instant failedAt,
            Instant nextAttemptAt,
            String errorCode,
            boolean deadLetter) {}
}
