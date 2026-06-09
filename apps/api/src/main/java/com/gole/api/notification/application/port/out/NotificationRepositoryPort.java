package com.gole.api.notification.application.port.out;

import com.gole.api.notification.domain.model.Notification;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port: 알림 영속성.
 */
public interface NotificationRepositoryPort {

    Notification save(Notification notification);

    /** 수신자의 알림을 최신순으로 조회. (N2) */
    List<Notification> findByRecipientNewestFirst(String recipientId);

    long countUnread(String recipientId);

    Optional<Notification> findById(String id);

    /** 수신자의 모든 알림을 읽음 처리. (N5) */
    void markAllRead(String recipientId);
}
