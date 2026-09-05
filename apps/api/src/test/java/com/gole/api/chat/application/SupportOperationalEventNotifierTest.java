package com.gole.api.chat.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.chat.application.port.out.SupportNotificationOutboxPort;
import com.gole.api.chat.domain.model.SupportCategory;
import com.gole.api.chat.domain.model.SupportNotificationEvent;
import com.gole.api.chat.domain.model.SupportNotificationEvent.EventType;
import com.gole.api.chat.domain.model.SupportTicket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SupportOperationalEventNotifierTest {

    private static final Instant NOW = Instant.parse("2026-09-04T01:02:03Z");

    private final RecordingOutbox outbox = new RecordingOutbox();
    private final SupportOperationalEventNotifier notifier = new SupportOperationalEventNotifier(outbox);

    @Test
    void openedEnqueuesOnlyNonIdentifyingMetadata() {
        SupportTicket ticket =
                SupportTicket.opened("private-room-id", "private-requester-id", SupportCategory.PRODUCT_FEEDBACK, NOW);

        notifier.opened(ticket);

        assertThat(outbox.events).singleElement().satisfies(event -> {
            assertThat(event.eventId()).matches("^[0-9a-f-]{36}$");
            assertThat(event.type()).isEqualTo(EventType.OPENED);
            assertThat(event.supportCategory()).isEqualTo(SupportCategory.PRODUCT_FEEDBACK);
            assertThat(event.ticketStatus()).isEqualTo(ticket.status());
            assertThat(event.occurredAt()).isEqualTo(NOW);
            assertThat(event.toString())
                    .doesNotContain("private-room-id", "private-requester-id", "문의 제목", "문의 내용", "이메일", "전화");
        });
    }

    @Test
    void requesterReplyGetsASeparateDurableEventId() {
        SupportTicket ticket = SupportTicket.opened("room-2", "requester-2", SupportCategory.GENERAL, NOW);

        notifier.requesterReplied(ticket);
        notifier.requesterReplied(ticket);

        assertThat(outbox.events).extracting(SupportNotificationEvent::type).containsOnly(EventType.REQUESTER_REPLIED);
        assertThat(outbox.events).extracting(SupportNotificationEvent::eventId).doesNotHaveDuplicates();
    }

    private static final class RecordingOutbox implements SupportNotificationOutboxPort {

        private final List<SupportNotificationEvent> events = new ArrayList<>();

        @Override
        public void enqueue(SupportNotificationEvent event) {
            events.add(event);
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
            return Optional.empty();
        }

        @Override
        public Optional<SupportNotificationEvent> findById(String eventId) {
            return events.stream()
                    .filter(event -> event.eventId().equals(eventId))
                    .findFirst();
        }
    }
}
