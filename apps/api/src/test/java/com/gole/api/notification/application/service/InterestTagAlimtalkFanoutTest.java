package com.gole.api.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.notification.application.port.out.AlimtalkDailyQuotaPort;
import com.gole.api.notification.application.port.out.AlimtalkSenderPort;
import com.gole.api.notification.application.port.out.InterestTagAlimtalkOutboxPort;
import com.gole.api.notification.application.port.out.InterestTagRecipientPort;
import com.gole.api.notification.application.port.out.ListingSnapshotPort;
import com.gole.api.notification.domain.model.InterestTagAlimtalkEvent;
import com.gole.api.notification.domain.model.InterestTagAlimtalkEvent.State;
import com.gole.api.notification.domain.model.InterestTagAlimtalkEvent.Type;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InterestTagAlimtalkFanoutTest {

    private static final Instant NOW = Instant.parse("2026-09-11T01:02:03Z");

    @Test
    void advancesCursorAndContinuesWithoutIncreasingAttemptsAfterPagesPerLease() {
        FakeOutbox outbox = new FakeOutbox();
        FakeRecipients recipients =
                new FakeRecipients(List.of("seller", "account-2"), List.of("account-3", "account-4"));
        InterestTagAlimtalkEvent event = claimedFanout(4, "account-0", 1);
        outbox.current = event;

        worker(outbox, recipients, new FakeQuota(), properties(2, 2, 10)).process(event);

        assertThat(recipients.cursors).containsExactly("account-0", "account-2");
        assertThat(outbox.current.state()).isEqualTo(State.PENDING);
        assertThat(outbox.current.cursorAccountId()).isEqualTo("account-4");
        assertThat(outbox.current.enqueuedRecipients()).isEqualTo(4);
        assertThat(outbox.current.attempts()).isEqualTo(4);
        assertThat(outbox.retry).isNull();
    }

    @Test
    void completesFanoutWhenTheLastPageIsShort() {
        FakeOutbox outbox = new FakeOutbox();
        FakeRecipients recipients = new FakeRecipients(List.of("account-1"));
        InterestTagAlimtalkEvent event = claimedFanout(1, null, 0);

        worker(outbox, recipients, new FakeQuota(), properties(3, 2, 10)).process(event);

        assertThat(outbox.delivered).containsExactly(event.eventId(), event.leaseToken());
        assertThat(outbox.enqueued)
                .extracting(InterestTagAlimtalkEvent::recipientAccountId)
                .containsExactly("account-1");
        assertThat(outbox.continued).isFalse();
    }

    @Test
    void excludesSellerAndAccountsOverTheirQuota() {
        FakeOutbox outbox = new FakeOutbox();
        FakeRecipients recipients = new FakeRecipients(List.of("seller", "allowed", "over-quota"));
        FakeQuota quota = new FakeQuota();
        quota.denied.add("over-quota");
        InterestTagAlimtalkEvent event = claimedFanout(1, null, 0);

        worker(outbox, recipients, quota, properties(10, 1, 10)).process(event);

        assertThat(outbox.enqueued)
                .extracting(InterestTagAlimtalkEvent::recipientAccountId)
                .containsExactly("allowed");
        assertThat(quota.acquired).containsExactly("allowed", "over-quota");
        assertThat(quota.acquired).doesNotContain("seller");
        assertThat(outbox.delivered).containsExactly(event.eventId(), event.leaseToken());
    }

    @Test
    void stopsAtMaximumRecipientsEvenWhenMoreRecipientsAreAvailable() {
        FakeOutbox outbox = new FakeOutbox();
        FakeRecipients recipients = new FakeRecipients(List.of("account-1", "account-2", "account-3"));
        InterestTagAlimtalkEvent event = claimedFanout(1, null, 0);

        worker(outbox, recipients, new FakeQuota(), properties(3, 3, 2)).process(event);

        assertThat(outbox.enqueued)
                .extracting(InterestTagAlimtalkEvent::recipientAccountId)
                .containsExactly("account-1", "account-2");
        assertThat(outbox.delivered).containsExactly(event.eventId(), event.leaseToken());
        assertThat(outbox.continued).isFalse();
    }

    private static InterestTagAlimtalkOutboxWorker worker(
            FakeOutbox outbox,
            FakeRecipients recipients,
            AlimtalkDailyQuotaPort quota,
            InterestTagAlimtalkProperties properties) {
        ListingSnapshotPort listings =
                listingId -> Optional.of(new ListingSnapshotPort.ListingSnapshot(listingId, true));
        return new InterestTagAlimtalkOutboxWorker(
                outbox,
                recipients,
                listings,
                quota,
                Optional.<AlimtalkSenderPort>empty(),
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static InterestTagAlimtalkProperties properties(int pageSize, int pagesPerLease, int maximumRecipients) {
        InterestTagAlimtalkProperties properties = new InterestTagAlimtalkProperties();
        properties.setRecipientPageSize(pageSize);
        properties.setPagesPerLease(pagesPerLease);
        properties.setMaxRecipientsPerListing(maximumRecipients);
        properties.setPollInterval(Duration.ofSeconds(7));
        return properties;
    }

    private static InterestTagAlimtalkEvent claimedFanout(int attempts, String cursor, long enqueuedRecipients) {
        return new InterestTagAlimtalkEvent(
                "fanout:listing-1",
                Type.FANOUT,
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
                null,
                cursor,
                enqueuedRecipients);
    }

    private static final class FakeRecipients implements InterestTagRecipientPort {

        private final ArrayDeque<List<String>> pages;
        private final List<String> cursors = new ArrayList<>();

        @SafeVarargs
        private FakeRecipients(List<String>... pages) {
            this.pages = new ArrayDeque<>(List.of(pages));
        }

        @Override
        public List<String> listEligibleAccountIds(String tagKey, String afterAccountId, int limit) {
            cursors.add(afterAccountId);
            return pages.isEmpty() ? List.of() : pages.removeFirst();
        }

        @Override
        public Optional<Recipient> resolveEligible(String accountId, String tagKey) {
            return Optional.empty();
        }
    }

    private static final class FakeQuota implements AlimtalkDailyQuotaPort {

        private final Set<String> denied = new HashSet<>();
        private final List<String> acquired = new ArrayList<>();

        @Override
        public boolean acquire(String accountId, int maximum, Duration window) {
            acquired.add(accountId);
            return !denied.contains(accountId);
        }
    }

    private static final class FakeOutbox implements InterestTagAlimtalkOutboxPort {

        private final List<InterestTagAlimtalkEvent> enqueued = new ArrayList<>();
        private InterestTagAlimtalkEvent current;
        private List<String> delivered = List.of();
        private boolean continued;
        private Retry retry;

        @Override
        public void enqueue(InterestTagAlimtalkEvent event) {
            enqueued.add(event);
        }

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
                Instant nextAttemptAt) {
            continued = true;
            current = new InterestTagAlimtalkEvent(
                    current.eventId(),
                    current.type(),
                    State.PENDING,
                    current.attempts(),
                    nextAttemptAt,
                    null,
                    null,
                    current.lastErrorCode(),
                    current.occurredAt(),
                    current.createdAt(),
                    null,
                    current.listingId(),
                    current.sellerId(),
                    current.interestTagKey(),
                    current.interestTagLabel(),
                    current.listingTitle(),
                    null,
                    cursorAccountId,
                    enqueuedRecipients);
        }

        @Override
        public void skipped(String eventId, String leaseToken, String reasonCode, Instant skippedAt) {}

        @Override
        public void retry(
                String eventId,
                String leaseToken,
                Instant failedAt,
                Instant nextAttemptAt,
                String errorCode,
                boolean deadLetter) {
            retry = new Retry(eventId, errorCode, deadLetter);
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

    private record Retry(String eventId, String errorCode, boolean deadLetter) {}
}
