package com.gole.api.notification.application.service;

import com.gole.api.notification.application.port.out.AlimtalkDailyQuotaPort;
import com.gole.api.notification.application.port.out.AlimtalkSendException;
import com.gole.api.notification.application.port.out.AlimtalkSendException.FailureType;
import com.gole.api.notification.application.port.out.AlimtalkSenderPort;
import com.gole.api.notification.application.port.out.AlimtalkSenderPort.SendAlimtalkCommand;
import com.gole.api.notification.application.port.out.InterestTagAlimtalkOutboxPort;
import com.gole.api.notification.application.port.out.InterestTagRecipientPort;
import com.gole.api.notification.application.port.out.ListingSnapshotPort;
import com.gole.api.notification.domain.model.InterestTagAlimtalkEvent;
import com.gole.api.notification.domain.model.InterestTagAlimtalkEvent.Type;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 관심태그 FANOUT을 DELIVERY로 나누고 발송 직전 자격을 재검증하는 lease 워커. */
@Component
public class InterestTagAlimtalkOutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(InterestTagAlimtalkOutboxWorker.class);

    private final InterestTagAlimtalkOutboxPort outbox;
    private final InterestTagRecipientPort recipients;
    private final ListingSnapshotPort listings;
    private final AlimtalkDailyQuotaPort quota;
    private final Optional<AlimtalkSenderPort> sender;
    private final InterestTagAlimtalkProperties properties;
    private final Clock clock;

    public InterestTagAlimtalkOutboxWorker(
            InterestTagAlimtalkOutboxPort outbox,
            InterestTagRecipientPort recipients,
            ListingSnapshotPort listings,
            AlimtalkDailyQuotaPort quota,
            Optional<AlimtalkSenderPort> sender,
            InterestTagAlimtalkProperties properties,
            Clock clock) {
        this.outbox = outbox;
        this.recipients = recipients;
        this.listings = listings;
        this.quota = quota;
        this.sender = sender;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${gole.interest-tag-alimtalk.poll-interval:PT10S}")
    public void drain() {
        if (!properties.enabled()) {
            return;
        }
        for (int processed = 0; processed < properties.batchSize(); processed++) {
            Instant claimedAt = Instant.now(clock);
            Optional<InterestTagAlimtalkEvent> claimed =
                    outbox.claimNext(claimedAt, properties.leaseDuration(), properties.maximumAttempts());
            if (claimed.isEmpty()) {
                return;
            }
            process(claimed.orElseThrow());
        }
    }

    void process(InterestTagAlimtalkEvent event) {
        try {
            if (event.type() == Type.FANOUT) {
                processFanout(event);
            } else {
                processDelivery(event);
            }
        } catch (AlimtalkSendException failure) {
            handleAlimtalkFailure(event, failure);
        } catch (RuntimeException failure) {
            retry(event, "PROCESSING_FAILURE", true);
        }
    }

    private void processFanout(InterestTagAlimtalkEvent event) {
        if (!isListingActive(event.listingId())) {
            outbox.skipped(event.eventId(), event.leaseToken(), "LISTING_NOT_ACTIVE", Instant.now(clock));
            return;
        }

        String cursor = event.cursorAccountId();
        long enqueued = event.enqueuedRecipients();
        if (enqueued >= properties.maxRecipientsPerListing()) {
            outbox.delivered(event.eventId(), event.leaseToken(), Instant.now(clock));
            return;
        }

        for (int pageNumber = 0; pageNumber < properties.pagesPerLease(); pageNumber++) {
            int pageSize = properties.recipientPageSize();
            List<String> page = recipients.listEligibleAccountIds(event.interestTagKey(), cursor, pageSize);
            if (page.isEmpty()) {
                outbox.delivered(event.eventId(), event.leaseToken(), Instant.now(clock));
                return;
            }

            String nextCursor = page.getLast();
            for (String accountId : page) {
                if (enqueued >= properties.maxRecipientsPerListing()) {
                    outbox.delivered(event.eventId(), event.leaseToken(), Instant.now(clock));
                    return;
                }
                if (accountId.equals(event.sellerId())) {
                    continue;
                }
                if (!quota.acquire(accountId, properties.dailyLimitPerAccount(), properties.quotaWindow())) {
                    continue;
                }
                outbox.enqueue(InterestTagAlimtalkEvent.delivery(event, accountId, Instant.now(clock)));
                enqueued++;
            }

            cursor = nextCursor;
            if (enqueued >= properties.maxRecipientsPerListing() || page.size() < pageSize) {
                outbox.delivered(event.eventId(), event.leaseToken(), Instant.now(clock));
                return;
            }
        }

        outbox.continueFanout(
                event.eventId(),
                event.leaseToken(),
                cursor,
                enqueued,
                Instant.now(clock).plus(properties.pollInterval()));
    }

    private void processDelivery(InterestTagAlimtalkEvent event) {
        Optional<InterestTagRecipientPort.Recipient> recipient =
                recipients.resolveEligible(event.recipientAccountId(), event.interestTagKey());
        if (recipient.isEmpty()) {
            outbox.skipped(event.eventId(), event.leaseToken(), "RECIPIENT_NOT_ELIGIBLE", Instant.now(clock));
            return;
        }
        if (!isListingActive(event.listingId())) {
            outbox.skipped(event.eventId(), event.leaseToken(), "LISTING_NOT_ACTIVE", Instant.now(clock));
            return;
        }
        if (sender.isEmpty()) {
            deadLetter(event, "ALIMTALK_SENDER_UNAVAILABLE");
            return;
        }

        sender.orElseThrow()
                .send(new SendAlimtalkCommand(
                        recipient.orElseThrow().phoneNumber(), properties.templateId(), variables(event)));
        outbox.delivered(event.eventId(), event.leaseToken(), Instant.now(clock));
    }

    private boolean isListingActive(String listingId) {
        return listings.findById(listingId)
                .map(ListingSnapshotPort.ListingSnapshot::active)
                .orElse(false);
    }

    private Map<String, String> variables(InterestTagAlimtalkEvent event) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put(properties.themeVariable(), event.interestTagLabel());
        variables.put(properties.titleVariable(), displayTitle(event.listingTitle()));
        variables.put(properties.linkVariable(), properties.listingUrlPrefix() + event.listingId());
        return variables;
    }

    private static String displayTitle(String title) {
        String normalized = title == null || title.isBlank() ? "새 매물" : title.trim();
        return normalized.length() <= 40 ? normalized : normalized.substring(0, 39) + "…";
    }

    private void handleAlimtalkFailure(InterestTagAlimtalkEvent event, AlimtalkSendException failure) {
        FailureType type = failure.getFailureType();
        boolean retryable = type == FailureType.RATE_LIMITED || type == FailureType.PROVIDER_FAILURE;
        retry(event, type.name(), retryable);
    }

    private void retry(InterestTagAlimtalkEvent event, String errorCode, boolean retryable) {
        Instant failedAt = Instant.now(clock);
        boolean deadLetter = !retryable || event.attempts() >= properties.maximumAttempts();
        Duration delay = deliveryDelay(event.attempts());
        String safeCode = safeErrorCode(errorCode);
        outbox.retry(event.eventId(), event.leaseToken(), failedAt, failedAt.plus(delay), safeCode, deadLetter);
        log.warn(
                "Interest-tag alimtalk processing failed; eventId={}, type={}, listingId={}, recipientAccountId={}, attempts={}, errorCode={}, deadLetter={}",
                event.eventId(),
                event.type(),
                event.listingId(),
                event.recipientAccountId(),
                event.attempts(),
                safeCode,
                deadLetter);
    }

    private void deadLetter(InterestTagAlimtalkEvent event, String errorCode) {
        retry(event, errorCode, false);
    }

    private Duration deliveryDelay(int attempts) {
        int shift = Math.min(Math.max(attempts - 1, 0), 20);
        try {
            return min(properties.initialBackoff().multipliedBy(1L << shift), properties.maximumBackoff());
        } catch (ArithmeticException overflow) {
            return properties.maximumBackoff();
        }
    }

    private static Duration min(Duration first, Duration second) {
        return first.compareTo(second) <= 0 ? first : second;
    }

    private static String safeErrorCode(String value) {
        if (value == null || !value.matches("^[A-Z0-9_]{1,80}$")) {
            return "DELIVERY_FAILURE";
        }
        return value;
    }
}
