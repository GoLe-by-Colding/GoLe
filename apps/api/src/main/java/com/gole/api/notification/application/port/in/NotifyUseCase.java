package com.gole.api.notification.application.port.in;

import com.gole.api.notification.domain.model.NotificationType;

/**
 * Inbound port: 알림 발생(다른 컨텍스트가 호출). (알림 스펙 N1, N6)
 */
public interface NotifyUseCase {

    String notify(NotifyCommand command);

    /**
     * @param recipientId 수신자(계정) id
     * @param type        알림 종류
     * @param message     표시 메시지
     * @param link        클릭 시 이동 경로(nullable)
     */
    record NotifyCommand(
            String recipientId, NotificationType type, String message, String link, String deduplicationKey) {

        public NotifyCommand(String recipientId, NotificationType type, String message, String link) {
            this(recipientId, type, message, link, null);
        }
    }
}
