package com.gole.api.notification.application.port.in;

import com.gole.api.notification.domain.model.Notification;
import java.util.List;

/**
 * Inbound port: 알림 조회/읽음 처리. (알림 스펙 N2~N5)
 */
public interface GetNotificationsUseCase {

    List<Notification> list(String recipientId);

    long unreadCount(String recipientId);

    void markRead(String notificationId, String recipientId);

    void markAllRead(String recipientId);
}
