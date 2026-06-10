package com.gole.api.notification.application.service;

import com.gole.api.notification.application.port.in.GetNotificationsUseCase;
import com.gole.api.notification.application.port.in.NotifyUseCase;
import com.gole.api.notification.application.port.out.NotificationIdGeneratorPort;
import com.gole.api.notification.application.port.out.NotificationRepositoryPort;
import com.gole.api.notification.domain.model.Notification;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 알림 유스케이스 구현. inbound port를 구현하고 outbound port에만 의존한다.
 * (알림 스펙 N1~N5)
 */
@Service
public class NotificationService implements NotifyUseCase, GetNotificationsUseCase {

    private final NotificationRepositoryPort repository;
    private final NotificationIdGeneratorPort idGenerator;
    private final Clock clock;

    public NotificationService(
            NotificationRepositoryPort repository, NotificationIdGeneratorPort idGenerator, Clock clock) {
        this.repository = repository;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    public String notify(NotifyCommand command) {
        Notification notification = Notification.create(
                idGenerator.newNotificationId(),
                command.recipientId(),
                command.type(),
                command.message(),
                command.link(),
                Instant.now(clock));
        return repository.save(notification).getId();
    }

    @Override
    public List<Notification> list(String recipientId) {
        return repository.findByRecipientNewestFirst(recipientId);
    }

    @Override
    public long unreadCount(String recipientId) {
        return repository.countUnread(recipientId);
    }

    @Override
    public void markRead(String notificationId, String recipientId) {
        repository
                .findById(notificationId)
                // 소유자 검증: 타인 알림이면 무시(N4)
                .filter(n -> n.getRecipientId().equals(recipientId))
                .ifPresent(n -> {
                    n.markRead();
                    repository.save(n);
                });
    }

    @Override
    public void markAllRead(String recipientId) {
        repository.markAllRead(recipientId);
    }
}
