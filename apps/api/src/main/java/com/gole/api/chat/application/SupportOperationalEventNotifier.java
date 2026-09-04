package com.gole.api.chat.application;

import com.gole.api.chat.application.port.out.SupportNotificationOutboxPort;
import com.gole.api.chat.domain.model.SupportNotificationEvent;
import com.gole.api.chat.domain.model.SupportNotificationEvent.EventType;
import com.gole.api.chat.domain.model.SupportTicket;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** 문의 트랜잭션 안에 본문·방·요청자 식별자 없는 Discord 알림 이벤트를 함께 적재한다. */
@Component
public class SupportOperationalEventNotifier {

    private final SupportNotificationOutboxPort outbox;

    public SupportOperationalEventNotifier(SupportNotificationOutboxPort outbox) {
        this.outbox = outbox;
    }

    public void opened(SupportTicket ticket) {
        enqueue(ticket, EventType.OPENED);
    }

    public void requesterReplied(SupportTicket ticket) {
        enqueue(ticket, EventType.REQUESTER_REPLIED);
    }

    private void enqueue(SupportTicket ticket, EventType type) {
        Instant now = ticket.updatedAt();
        outbox.enqueue(SupportNotificationEvent.pending(type, ticket.category(), ticket.status(), now, now));
    }
}
