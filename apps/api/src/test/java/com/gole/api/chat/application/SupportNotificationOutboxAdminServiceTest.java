package com.gole.api.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.chat.application.SupportNotificationOutboxAdminService.RequeueReasonCode;
import com.gole.api.chat.application.port.out.SupportNotificationOutboxPort;
import com.gole.api.chat.domain.model.SupportCategory;
import com.gole.api.chat.domain.model.SupportNotificationEvent;
import com.gole.api.chat.domain.model.SupportNotificationEvent.EventType;
import com.gole.api.chat.domain.model.SupportNotificationEvent.State;
import com.gole.api.chat.domain.model.SupportStatus;
import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.exception.ConflictException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SupportNotificationOutboxAdminServiceTest {

    private static final String EVENT_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final Instant NOW = Instant.parse("2026-09-04T12:00:00Z");

    @Test
    void deadLetterOnlyIsRequeuedAndImmediateReplayIsIdempotent() {
        FakeOutbox outbox = new FakeOutbox(event(State.DEAD_LETTER, 12));
        SupportNotificationOutboxAdminService service = service(outbox, true);
        String confirmation = SupportNotificationOutboxAdminService.expectedConfirmation(EVENT_ID);

        var first = service.requeue(EVENT_ID, confirmation, RequeueReasonCode.WEBHOOK_CONFIGURATION_RESTORED);
        var replay = service.requeue(EVENT_ID, confirmation, RequeueReasonCode.WEBHOOK_CONFIGURATION_RESTORED);

        assertThat(first.changed()).isTrue();
        assertThat(first.event().state()).isEqualTo(State.PENDING);
        assertThat(first.event().attempts()).isZero();
        assertThat(first.event().nextAttemptAt()).isEqualTo(NOW);
        assertThat(replay.changed()).isFalse();
        assertThat(replay.event().state()).isEqualTo(State.PENDING);
    }

    @Test
    void exactEventBoundConfirmationIsRequiredBeforeRepositoryAccess() {
        FakeOutbox outbox = new FakeOutbox(event(State.DEAD_LETTER, 12));

        assertThatThrownBy(() ->
                        service(outbox, true).requeue(EVENT_ID, EVENT_ID, RequeueReasonCode.DISCORD_INCIDENT_RESOLVED))
                .isInstanceOf(BadRequestException.class)
                .extracting(failure -> ((BadRequestException) failure).getCode())
                .isEqualTo("SUPPORT_NOTIFICATION_REQUEUE_CONFIRMATION_MISMATCH");
        assertThat(outbox.requeueCalls).isZero();
    }

    @Test
    void disabledDeliveryAndAlreadyDeliveredReceiptFailClosed() {
        String confirmation = SupportNotificationOutboxAdminService.expectedConfirmation(EVENT_ID);
        FakeOutbox dead = new FakeOutbox(event(State.DEAD_LETTER, 12));
        assertThatThrownBy(() -> service(dead, false)
                        .requeue(EVENT_ID, confirmation, RequeueReasonCode.DISCORD_INCIDENT_RESOLVED))
                .isInstanceOf(ConflictException.class)
                .extracting(failure -> ((ConflictException) failure).getCode())
                .isEqualTo("SUPPORT_NOTIFICATION_DELIVERY_DISABLED");

        FakeOutbox delivered = new FakeOutbox(event(State.DELIVERED, 1));
        assertThatThrownBy(() -> service(delivered, true)
                        .requeue(EVENT_ID, confirmation, RequeueReasonCode.MANUAL_DELIVERY_RETRY_APPROVED))
                .isInstanceOf(ConflictException.class)
                .extracting(failure -> ((ConflictException) failure).getCode())
                .isEqualTo("SUPPORT_NOTIFICATION_ALREADY_DELIVERED");
    }

    private static SupportNotificationOutboxAdminService service(FakeOutbox outbox, boolean enabled) {
        SupportNotificationOutboxProperties properties = new SupportNotificationOutboxProperties();
        properties.setProcessingEnabled(enabled);
        return new SupportNotificationOutboxAdminService(outbox, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static SupportNotificationEvent event(State state, int attempts) {
        Instant next = state == State.PENDING || state == State.IN_FLIGHT ? NOW : null;
        return new SupportNotificationEvent(
                EVENT_ID,
                EventType.OPENED,
                SupportCategory.GENERAL,
                SupportStatus.UNASSIGNED,
                state,
                attempts,
                next,
                state == State.IN_FLIGHT ? "lease" : null,
                state == State.IN_FLIGHT ? NOW.plusSeconds(30) : null,
                state == State.DEAD_LETTER ? "HTTP_503" : null,
                NOW.minusSeconds(60),
                NOW.minusSeconds(60),
                state == State.DELIVERED ? NOW.minusSeconds(30) : null);
    }

    private static final class FakeOutbox implements SupportNotificationOutboxPort {

        private SupportNotificationEvent current;
        private int requeueCalls;

        private FakeOutbox(SupportNotificationEvent current) {
            this.current = current;
        }

        @Override
        public void enqueue(SupportNotificationEvent event) {
            current = event;
        }

        @Override
        public Optional<SupportNotificationEvent> claimNext(Instant now, Duration leaseDuration, int maximumAttempts) {
            return Optional.empty();
        }

        @Override
        public void delivered(String eventId, String leaseToken, Instant deliveredAt) {}

        @Override
        public void retry(
                String eventId,
                String leaseToken,
                Instant failedAt,
                Instant nextAttemptAt,
                String errorCode,
                boolean deadLetter) {}

        @Override
        public Optional<SupportNotificationEvent> requeueDeadLetter(String eventId, Instant requeuedAt) {
            requeueCalls++;
            if (!current.eventId().equals(eventId) || current.state() != State.DEAD_LETTER) {
                return Optional.empty();
            }
            current = new SupportNotificationEvent(
                    current.eventId(),
                    current.type(),
                    current.supportCategory(),
                    current.ticketStatus(),
                    State.PENDING,
                    0,
                    requeuedAt,
                    null,
                    null,
                    null,
                    current.occurredAt(),
                    current.createdAt(),
                    null);
            return Optional.of(current);
        }

        @Override
        public Optional<SupportNotificationEvent> findById(String eventId) {
            return current.eventId().equals(eventId) ? Optional.of(current) : Optional.empty();
        }
    }
}
