package com.gole.api.notification.application.port.out;

import com.gole.api.notification.domain.model.InterestTagAlimtalkEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** 관심태그 알림톡 FANOUT/DELIVERY의 원자적 enqueue·lease·완료 원장 포트. */
public interface InterestTagAlimtalkOutboxPort {

    void enqueue(InterestTagAlimtalkEvent event);

    Optional<InterestTagAlimtalkEvent> claimNext(Instant now, Duration leaseDuration, int maximumAttempts);

    void delivered(String eventId, String leaseToken, Instant deliveredAt);

    void continueFanout(
            String eventId, String leaseToken, String cursorAccountId, long enqueuedRecipients, Instant nextAttemptAt);

    void skipped(String eventId, String leaseToken, String reasonCode, Instant skippedAt);

    void retry(
            String eventId,
            String leaseToken,
            Instant failedAt,
            Instant nextAttemptAt,
            String errorCode,
            boolean deadLetter);

    Optional<InterestTagAlimtalkEvent> requeueDeadLetter(String eventId, Instant requeuedAt);

    Optional<InterestTagAlimtalkEvent> findById(String eventId);
}
