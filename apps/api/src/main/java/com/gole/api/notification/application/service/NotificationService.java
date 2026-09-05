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

    /** 단말 알림 제목. 본문은 인앱 알림과 같은 문구를 쓴다. */
    private static final String PUSH_TITLE = "GoLe";

    private final NotificationRepositoryPort repository;
    private final NotificationIdGeneratorPort idGenerator;
    private final PushDispatcher pushDispatcher;
    private final Clock clock;

    public NotificationService(
            NotificationRepositoryPort repository,
            NotificationIdGeneratorPort idGenerator,
            PushDispatcher pushDispatcher,
            Clock clock) {
        this.repository = repository;
        this.idGenerator = idGenerator;
        this.pushDispatcher = pushDispatcher;
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
                command.deduplicationKey(),
                Instant.now(clock));
        Notification stored = repository.saveOnce(notification);

        // saveOnce는 멱등 키가 겹치면 <b>기존</b> 알림을 돌려준다. 그때 다시 푸시하면
        // 같은 사건으로 단말이 여러 번 울린다 — 새로 저장된 경우에만 민다. (R8.2)
        if (stored.getId().equals(notification.getId())) {
            pushDispatcher.dispatch(stored.getRecipientId(), PUSH_TITLE, stored.getMessage(), stored.getLink());
        }
        return stored.getId();
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
