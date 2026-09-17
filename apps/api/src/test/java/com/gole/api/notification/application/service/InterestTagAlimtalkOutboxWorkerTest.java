package com.gole.api.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.notification.application.port.out.AlimtalkDailyQuotaPort;
import com.gole.api.notification.application.port.out.AlimtalkSendException;
import com.gole.api.notification.application.port.out.AlimtalkSendException.FailureType;
import com.gole.api.notification.application.port.out.AlimtalkSenderPort;
import com.gole.api.notification.application.port.out.InterestTagAlimtalkOutboxPort;
import com.gole.api.notification.application.port.out.InterestTagRecipientPort;
import com.gole.api.notification.application.port.out.ListingSnapshotPort;
import com.gole.api.notification.domain.model.InterestTagAlimtalkEvent;
import com.gole.api.notification.domain.model.InterestTagAlimtalkEvent.State;
import com.gole.api.notification.domain.model.InterestTagAlimtalkEvent.Type;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class InterestTagAlimtalkOutboxWorkerTest {

    private static final Instant NOW = Instant.parse("2026-09-11T01:02:03Z");

    @ParameterizedTest
    @EnumSource(FailureType.class)
    void mapsAllFailureTypesToRetryOrImmediateDeadLetter(FailureType failureType) {
        FakeOutbox outbox = new FakeOutbox();
        ThrowingSender sender = new ThrowingSender(failureType);

        worker(outbox, eligibleRecipients(), activeListing(), Optional.of(sender), properties())
                .process(delivery(1));

        boolean retryable = failureType == FailureType.RATE_LIMITED || failureType == FailureType.PROVIDER_FAILURE;
        assertThat(outbox.retry)
                .isEqualTo(new Retry(
                        "listing-1:recipient-1", "lease-1", NOW, NOW.plusSeconds(10), failureType.name(), !retryable));
    }

    @Test
    void skipsDeliveryWhenRecipientIsNoLongerEligible() {
        FakeOutbox outbox = new FakeOutbox();
        RecordingSender sender = new RecordingSender();
        InterestTagRecipientPort recipients = recipients(Optional.empty());

        worker(outbox, recipients, activeListing(), Optional.of(sender), properties())
                .process(delivery(1));

        assertThat(outbox.skipped)
                .isEqualTo(new Skipped("listing-1:recipient-1", "lease-1", "RECIPIENT_NOT_ELIGIBLE", NOW));
        assertThat(sender.commands).isEmpty();
        assertThat(outbox.retry).isNull();
    }

    @Test
    void skipsDeliveryWhenListingIsNoLongerActive() {
        FakeOutbox outbox = new FakeOutbox();
        RecordingSender sender = new RecordingSender();

        worker(outbox, eligibleRecipients(), inactiveListing(), Optional.of(sender), properties())
                .process(delivery(1));

        assertThat(outbox.skipped)
                .isEqualTo(new Skipped("listing-1:recipient-1", "lease-1", "LISTING_NOT_ACTIVE", NOW));
        assertThat(sender.commands).isEmpty();
        assertThat(outbox.retry).isNull();
    }

    @Test
    void deadLettersImmediatelyWhenSenderIsUnavailable() {
        FakeOutbox outbox = new FakeOutbox();

        worker(outbox, eligibleRecipients(), activeListing(), Optional.empty(), properties())
                .process(delivery(1));

        assertThat(outbox.retry)
                .isEqualTo(new Retry(
                        "listing-1:recipient-1",
                        "lease-1",
                        NOW,
                        NOW.plusSeconds(10),
                        "ALIMTALK_SENDER_UNAVAILABLE",
                        true));
    }

    @Test
    void exponentialBackoffIsClampedToMaximumBackoff() {
        FakeOutbox outbox = new FakeOutbox();
        InterestTagAlimtalkProperties properties = properties();
        properties.setMaximumBackoff(Duration.ofSeconds(30));
        ThrowingSender sender = new ThrowingSender(FailureType.PROVIDER_FAILURE);

        worker(outbox, eligibleRecipients(), activeListing(), Optional.of(sender), properties)
                .process(delivery(3));

        assertThat(outbox.retry.nextAttemptAt()).isEqualTo(NOW.plusSeconds(30));
        assertThat(outbox.retry.deadLetter()).isFalse();
    }

    @Test
    void retryableFailureDeadLettersOnTheLastAttempt() {
        FakeOutbox outbox = new FakeOutbox();
        InterestTagAlimtalkProperties properties = properties();
        properties.setMaximumAttempts(3);
        ThrowingSender sender = new ThrowingSender(FailureType.RATE_LIMITED);

        worker(outbox, eligibleRecipients(), activeListing(), Optional.of(sender), properties)
                .process(delivery(3));

        assertThat(outbox.retry.deadLetter()).isTrue();
        assertThat(outbox.retry.errorCode()).isEqualTo("RATE_LIMITED");
    }

    @Test
    void invalidErrorCodeFallsBackToDeliveryFailure() throws Exception {
        Method safeErrorCode = InterestTagAlimtalkOutboxWorker.class.getDeclaredMethod("safeErrorCode", String.class);
        safeErrorCode.setAccessible(true);

        assertThat(safeErrorCode.invoke(null, "invalid-code")).isEqualTo("DELIVERY_FAILURE");
        assertThat(safeErrorCode.invoke(null, "A".repeat(81))).isEqualTo("DELIVERY_FAILURE");
        assertThat(safeErrorCode.invoke(null, new Object[] {null})).isEqualTo("DELIVERY_FAILURE");
        assertThat(safeErrorCode.invoke(null, "HTTP_503")).isEqualTo("HTTP_503");
    }

    private static InterestTagAlimtalkOutboxWorker worker(
            FakeOutbox outbox,
            InterestTagRecipientPort recipients,
            ListingSnapshotPort listings,
            Optional<AlimtalkSenderPort> sender,
            InterestTagAlimtalkProperties properties) {
        AlimtalkDailyQuotaPort quota = (accountId, maximum, window) -> true;
        return new InterestTagAlimtalkOutboxWorker(
                outbox, recipients, listings, quota, sender, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static InterestTagAlimtalkProperties properties() {
        InterestTagAlimtalkProperties properties = new InterestTagAlimtalkProperties();
        properties.setTemplateId("template-1");
        properties.setMaximumAttempts(8);
        properties.setInitialBackoff(Duration.ofSeconds(10));
        properties.setMaximumBackoff(Duration.ofHours(1));
        return properties;
    }

    private static InterestTagRecipientPort eligibleRecipients() {
        return recipients(Optional.of(new InterestTagRecipientPort.Recipient("recipient-1", "TEST_PHONE")));
    }

    private static InterestTagRecipientPort recipients(Optional<InterestTagRecipientPort.Recipient> recipient) {
        return new InterestTagRecipientPort() {
            @Override
            public List<String> listEligibleAccountIds(String tagKey, String afterAccountId, int limit) {
                return List.of();
            }

            @Override
            public Optional<Recipient> resolveEligible(String accountId, String tagKey) {
                return recipient;
            }
        };
    }

    private static ListingSnapshotPort activeListing() {
        return listingId -> Optional.of(new ListingSnapshotPort.ListingSnapshot(listingId, true));
    }

    private static ListingSnapshotPort inactiveListing() {
        return listingId -> Optional.of(new ListingSnapshotPort.ListingSnapshot(listingId, false));
    }

    private static InterestTagAlimtalkEvent delivery(int attempts) {
        return new InterestTagAlimtalkEvent(
                "listing-1:recipient-1",
                Type.DELIVERY,
                State.IN_FLIGHT,
                attempts,
                NOW,
                "lease-1",
                NOW.plusSeconds(30),
                null,
                NOW.minusSeconds(2),
                NOW.minusSeconds(1),
                null,
                "listing-1",
                "seller",
                "technic",
                "테크닉",
                "새 테크닉 매물",
                "recipient-1",
                null,
                0);
    }

    private static final class ThrowingSender implements AlimtalkSenderPort {

        private final FailureType failureType;

        private ThrowingSender(FailureType failureType) {
            this.failureType = failureType;
        }

        @Override
        public AlimtalkAcceptance send(SendAlimtalkCommand command) {
            throw new AlimtalkSendException(failureType, "provider failure");
        }
    }

    private static final class RecordingSender implements AlimtalkSenderPort {

        private final List<SendAlimtalkCommand> commands = new ArrayList<>();

        @Override
        public AlimtalkAcceptance send(SendAlimtalkCommand command) {
            commands.add(command);
            return new AlimtalkAcceptance("group-1", "message-1", "2000", "accepted");
        }
    }

    private static final class FakeOutbox implements InterestTagAlimtalkOutboxPort {

        private List<String> delivered = List.of();
        private Skipped skipped;
        private Retry retry;

        @Override
        public void enqueue(InterestTagAlimtalkEvent event) {}

        @Override
        public Optional<InterestTagAlimtalkEvent> claimNext(Instant now, Duration leaseDuration, int maximumAttempts) {
            return Optional.empty();
        }

        @Override
        public void delivered(String eventId, String leaseToken, Instant deliveredAt) {
            delivered = List.of(eventId, leaseToken);
        }

        @Override
        public void continueFanout(
                String eventId,
                String leaseToken,
                String cursorAccountId,
                long enqueuedRecipients,
                Instant nextAttemptAt) {}

        @Override
        public void skipped(String eventId, String leaseToken, String reasonCode, Instant skippedAt) {
            skipped = new Skipped(eventId, leaseToken, reasonCode, skippedAt);
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
        public Optional<InterestTagAlimtalkEvent> requeueDeadLetter(String eventId, Instant requeuedAt) {
            return Optional.empty();
        }

        @Override
        public Optional<InterestTagAlimtalkEvent> findById(String eventId) {
            return Optional.empty();
        }
    }

    private record Skipped(String eventId, String leaseToken, String reasonCode, Instant skippedAt) {}

    private record Retry(
            String eventId,
            String leaseToken,
            Instant failedAt,
            Instant nextAttemptAt,
            String errorCode,
            boolean deadLetter) {}
}
