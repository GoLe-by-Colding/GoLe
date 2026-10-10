package com.gole.api.chat.application.port.in;

import com.gole.api.chat.domain.model.SupportNotificationEvent;
import com.gole.api.chat.domain.model.SupportOperator;

/**
 * Inbound port: 전달 실패(dead letter)한 문의 운영 알림 한 건을 다시 보낸다. 확인 문구는 이벤트 ID에 결박된다.
 * 실제로 다시 큐잉했을 때만 운영자를 관리자 감사 로그에 같은 트랜잭션으로 남긴다.
 */
public interface RequeueSupportNotificationUseCase {

    RequeueOutcome requeue(String eventId, String confirmation, RequeueReasonCode reasonCode, SupportOperator operator);

    enum RequeueReasonCode {
        WEBHOOK_CONFIGURATION_RESTORED,
        DISCORD_INCIDENT_RESOLVED,
        MANUAL_DELIVERY_RETRY_APPROVED
    }

    record RequeueOutcome(SupportNotificationEvent event, boolean changed) {}
}
