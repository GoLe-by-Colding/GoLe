package com.gole.api.chat.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.chat.application.port.in.RequeueSupportNotificationUseCase.RequeueReasonCode;
import com.gole.api.chat.application.port.out.SupportAdminActionPort;
import com.gole.api.chat.application.port.out.SupportNotificationOutboxPort;
import com.gole.api.chat.domain.model.SupportCategory;
import com.gole.api.chat.domain.model.SupportNotificationEvent;
import com.gole.api.chat.domain.model.SupportNotificationEvent.EventType;
import com.gole.api.chat.domain.model.SupportNotificationEvent.State;
import com.gole.api.chat.domain.model.SupportOperator;
import com.gole.api.chat.domain.model.SupportStatus;
import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.exception.ConflictException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SupportNotificationOutboxAdminServiceTest {

    private static final String EVENT_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final Instant NOW = Instant.parse("2026-09-04T12:00:00Z");
    private static final SupportOperator OPERATOR = new SupportOperator("admin-1", "admin@gole.test");

    private final List<String> audits = new ArrayList<>();

    @Test
    void deadLetterOnlyIsRequeuedAndImmediateReplayIsIdempotent() {
        FakeOutbox outbox = new FakeOutbox(event(State.DEAD_LETTER, 12));
        SupportNotificationOutboxAdminService service = service(outbox, true);
        String confirmation = SupportNotificationOutboxAdminService.expectedConfirmation(EVENT_ID);

        var first = service.requeue(EVENT_ID, confirmation, RequeueReasonCode.WEBHOOK_CONFIGURATION_RESTORED, OPERATOR);
        var replay =
                service.requeue(EVENT_ID, confirmation, RequeueReasonCode.WEBHOOK_CONFIGURATION_RESTORED, OPERATOR);

        assertThat(first.changed()).isTrue();
        assertThat(first.event().state()).isEqualTo(State.PENDING);
        assertThat(first.event().attempts()).isZero();
        assertThat(first.event().nextAttemptAt()).isEqualTo(NOW);
        assertThat(replay.changed()).isFalse();
        assertThat(replay.event().state()).isEqualTo(State.PENDING);
        assertThat(audits)
                .containsExactly("admin-1|SUPPORT_NOTIFICATION_REQUEUE|" + EVENT_ID
                        + "|reasonCode=WEBHOOK_CONFIGURATION_RESTORED");
    }

    @Test
    void exactEventBoundConfirmationIsRequiredBeforeRepositoryAccess() {
        FakeOutbox outbox = new FakeOutbox(event(State.DEAD_LETTER, 12));

        assertThatThrownBy(() -> service(outbox, true)
                        .requeue(EVENT_ID, EVENT_ID, RequeueReasonCode.DISCORD_INCIDENT_RESOLVED, OPERATOR))
                .isInstanceOf(BadRequestException.class)
                .extracting(failure -> ((BadRequestException) failure).getCode())
                .isEqualTo("SUPPORT_NOTIFICATION_REQUEUE_CONFIRMATION_MISMATCH");
        assertThat(outbox.requeueCalls).isZero();
        assertThat(audits).isEmpty();
    }

    @Test
    void disabledDeliveryAndAlreadyDeliveredReceiptFailClosed() {
        String confirmation = SupportNotificationOutboxAdminService.expectedConfirmation(EVENT_ID);
        FakeOutbox dead = new FakeOutbox(event(State.DEAD_LETTER, 12));
        assertThatThrownBy(() -> service(dead, false)
                        .requeue(EVENT_ID, confirmation, RequeueReasonCode.DISCORD_INCIDENT_RESOLVED, OPERATOR))
                .isInstanceOf(ConflictException.class)
                .extracting(failure -> ((ConflictException) failure).getCode())
                .isEqualTo("SUPPORT_NOTIFICATION_DELIVERY_DISABLED");

        FakeOutbox delivered = new FakeOutbox(event(State.DELIVERED, 1));
        assertThatThrownBy(() -> service(delivered, true)
                        .requeue(EVENT_ID, confirmation, RequeueReasonCode.MANUAL_DELIVERY_RETRY_APPROVED, OPERATOR))
                .isInstanceOf(ConflictException.class)
                .extracting(failure -> ((ConflictException) failure).getCode())
                .isEqualTo("SUPPORT_NOTIFICATION_ALREADY_DELIVERED");
    }

    private SupportNotificationOutboxAdminService service(FakeOutbox outbox, boolean enabled) {
        SupportNotificationOutboxProperties properties = new SupportNotificationOutboxProperties();
        properties.setProcessingEnabled(enabled);
        SupportAdminActionPort audit = (operator, action, targetId, detail) ->
                audits.add(String.join("|", operator.id(), action.name(), targetId, detail));
        return new SupportNotificationOutboxAdminService(outbox, properties, audit, Clock.fixed(NOW, ZoneOffset.UTC));
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
