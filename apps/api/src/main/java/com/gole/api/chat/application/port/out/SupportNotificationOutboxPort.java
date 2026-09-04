package com.gole.api.chat.application.port.out;

import com.gole.api.chat.domain.model.SupportNotificationEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** 문의 Discord 알림의 원자적 enqueue·lease claim·완료 원장 포트. */
public interface SupportNotificationOutboxPort {

    void enqueue(SupportNotificationEvent event);

    Optional<SupportNotificationEvent> claimNext(Instant now, Duration leaseDuration, int maximumAttempts);

    void delivered(String eventId, String leaseToken, Instant deliveredAt);

    void retry(
            String eventId,
            String leaseToken,
            Instant failedAt,
            Instant nextAttemptAt,
            String errorCode,
            boolean deadLetter);

    /** DEAD_LETTER 상태만 원자적으로 새 PENDING 시도로 되돌린다. */
    Optional<SupportNotificationEvent> requeueDeadLetter(String eventId, Instant requeuedAt);

    Optional<SupportNotificationEvent> findById(String eventId);
}
