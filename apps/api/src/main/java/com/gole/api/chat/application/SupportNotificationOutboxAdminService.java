package com.gole.api.chat.application;

import com.gole.api.chat.application.port.out.SupportNotificationOutboxPort;
import com.gole.api.chat.domain.model.SupportNotificationEvent;
import com.gole.api.chat.domain.model.SupportNotificationEvent.State;
import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.exception.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** 자동 재시도 한도를 소진한 비식별 문의 알림을 운영자 확인 뒤 한 번 더 큐잉한다. */
@Service
public class SupportNotificationOutboxAdminService {

    private static final String CONFIRMATION_PREFIX = "REQUEUE:";

    private final SupportNotificationOutboxPort outbox;
    private final SupportNotificationOutboxProperties properties;
    private final Clock clock;

    public SupportNotificationOutboxAdminService(
            SupportNotificationOutboxPort outbox, SupportNotificationOutboxProperties properties, Clock clock) {
        this.outbox = outbox;
        this.properties = properties;
        this.clock = clock;
    }

    public RequeueOutcome requeue(String eventId, String confirmation, RequeueReasonCode reasonCode) {
        Objects.requireNonNull(reasonCode, "reasonCode");
        if (!expectedConfirmation(eventId).equals(confirmation)) {
            throw new BadRequestException(
                    "SUPPORT_NOTIFICATION_REQUEUE_CONFIRMATION_MISMATCH", "알림 이벤트 ID에 결박된 재시도 확인 문구가 일치하지 않습니다");
        }
        if (!properties.isProcessingEnabled()) {
            throw new ConflictException(
                    "SUPPORT_NOTIFICATION_DELIVERY_DISABLED", "Discord 알림과 outbox processor를 정상화한 뒤 다시 시도해 주세요");
        }

        Instant now = Instant.now(clock);
        var requeued = outbox.requeueDeadLetter(eventId, now);
        if (requeued.isPresent()) {
            return new RequeueOutcome(requeued.orElseThrow(), true);
        }

        SupportNotificationEvent current = outbox.findById(eventId)
                .orElseThrow(
                        () -> new NotFoundException("SUPPORT_NOTIFICATION_NOT_FOUND", "재시도할 문의 알림 이벤트를 찾을 수 없습니다"));
        if (current.state() == State.PENDING || current.state() == State.IN_FLIGHT) {
            return new RequeueOutcome(current, false);
        }
        if (current.state() == State.DELIVERED) {
            throw new ConflictException("SUPPORT_NOTIFICATION_ALREADY_DELIVERED", "이미 Discord가 수락한 알림은 다시 큐잉할 수 없습니다");
        }
        throw new ConflictException("SUPPORT_NOTIFICATION_REQUEUE_CONFLICT", "알림 상태가 동시에 변경되었습니다. 상태를 새로 확인해 주세요");
    }

    public static String expectedConfirmation(String eventId) {
        return CONFIRMATION_PREFIX + eventId;
    }

    public enum RequeueReasonCode {
        WEBHOOK_CONFIGURATION_RESTORED,
        DISCORD_INCIDENT_RESOLVED,
        MANUAL_DELIVERY_RETRY_APPROVED
    }

    public record RequeueOutcome(SupportNotificationEvent event, boolean changed) {}
}
