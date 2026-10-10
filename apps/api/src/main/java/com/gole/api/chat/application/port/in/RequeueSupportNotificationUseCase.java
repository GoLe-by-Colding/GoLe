package com.gole.api.chat.application.port.in;

import com.gole.api.chat.domain.model.SupportNotificationEvent;

/** Inbound port: 전달 실패(dead letter)한 문의 운영 알림 한 건을 다시 보낸다. 확인 문구는 이벤트 ID에 결박된다. */
public interface RequeueSupportNotificationUseCase {

    RequeueOutcome requeue(String eventId, String confirmation, RequeueReasonCode reasonCode);

    enum RequeueReasonCode {
        WEBHOOK_CONFIGURATION_RESTORED,
        DISCORD_INCIDENT_RESOLVED,
        MANUAL_DELIVERY_RETRY_APPROVED
    }

    record RequeueOutcome(SupportNotificationEvent event, boolean changed) {}
}
