package com.gole.api.chat.application;

import com.gole.api.chat.application.port.out.SupportNotificationOutboxPort;
import com.gole.api.chat.domain.model.SupportNotificationEvent;
import com.gole.api.chat.domain.model.SupportNotificationEvent.EventType;
import com.gole.api.common.operations.ConfirmedOperationalEventPublisher;
import com.gole.api.common.operations.ConfirmedOperationalEventPublisher.DeliveryResult;
import com.gole.api.common.operations.ConfirmedOperationalEventPublisher.DeliveryStatus;
import com.gole.api.common.operations.OperationalEvent;
import com.gole.api.common.operations.OperationalEvent.Category;
import com.gole.api.common.operations.OperationalEvent.Level;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Mongo outbox를 lease로 점유해 Discord 수락 응답 뒤에만 완료 처리한다. */
@Component
public class SupportNotificationOutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(SupportNotificationOutboxWorker.class);
    private static final String ADMIN_PATH = "/admin/support";

    private final SupportNotificationOutboxPort outbox;
    private final ConfirmedOperationalEventPublisher publisher;
    private final SupportNotificationOutboxProperties properties;
    private final Clock clock;

    public SupportNotificationOutboxWorker(
            SupportNotificationOutboxPort outbox,
            ConfirmedOperationalEventPublisher publisher,
            SupportNotificationOutboxProperties properties,
            Clock clock) {
        this.outbox = outbox;
        this.publisher = publisher;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${gole.support-notification-outbox.poll-interval:PT10S}")
    public void drain() {
        if (!properties.isProcessingEnabled()) {
            return;
        }
        for (int processed = 0; processed < properties.getBatchSize(); processed++) {
            Instant claimedAt = Instant.now(clock);
            var claimed = outbox.claimNext(claimedAt, properties.getLeaseDuration(), properties.getMaximumAttempts());
            if (claimed.isEmpty()) {
                return;
            }
            process(claimed.orElseThrow());
        }
    }

    void process(SupportNotificationEvent event) {
        DeliveryResult result;
        try {
            result = publisher.publishAndConfirm(toOperationalEvent(event));
        } catch (RuntimeException failure) {
            result = DeliveryResult.retryable("PUBLISHER_" + failure.getClass().getSimpleName(), null);
        }
        Instant completedAt = Instant.now(clock);

        if (result.status() == DeliveryStatus.DELIVERED) {
            outbox.delivered(event.eventId(), event.leaseToken(), completedAt);
            return;
        }

        boolean deadLetter = result.status() == DeliveryStatus.PERMANENT_FAILURE
                || event.attempts() >= properties.getMaximumAttempts();
        Duration delay = deliveryDelay(event.attempts(), result.retryAfter());
        String errorCode = safeErrorCode(result.errorCode());
        outbox.retry(event.eventId(), event.leaseToken(), completedAt, completedAt.plus(delay), errorCode, deadLetter);
        log.warn(
                "Support notification delivery failed; durable outbox state updated (attempt={}, deadLetter={}, errorCode={})",
                event.attempts(),
                deadLetter,
                errorCode);
    }

    private OperationalEvent toOperationalEvent(SupportNotificationEvent event) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("이벤트 ID", event.eventId());
        fields.put("문의 유형", event.supportCategory().name());
        fields.put("처리 상태", event.ticketStatus().name());
        fields.put("관리자 경로", ADMIN_PATH);
        String title = event.type() == EventType.OPENED ? "새 운영 문의 접수" : "운영 문의 사용자 답변";
        String description = event.type() == EventType.OPENED
                ? "새 문의가 접수되었습니다. 관리자 문의함에서 확인해 주세요."
                : "사용자가 문의에 답변했습니다. 관리자 문의함에서 확인해 주세요.";
        return new OperationalEvent(Category.SUPPORT, Level.INFO, title, description, fields, event.occurredAt());
    }

    private Duration deliveryDelay(int attempts, Duration serverRequested) {
        if (serverRequested != null && !serverRequested.isNegative() && !serverRequested.isZero()) {
            return min(serverRequested, properties.getMaximumBackoff());
        }
        int shift = Math.min(Math.max(attempts - 1, 0), 20);
        try {
            return min(properties.getInitialBackoff().multipliedBy(1L << shift), properties.getMaximumBackoff());
        } catch (ArithmeticException overflow) {
            return properties.getMaximumBackoff();
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
