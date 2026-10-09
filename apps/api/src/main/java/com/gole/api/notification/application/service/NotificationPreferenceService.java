package com.gole.api.notification.application.service;

import com.gole.api.notification.application.port.in.ManageNotificationPreferencesUseCase;
import com.gole.api.notification.application.port.out.NotificationPreferenceRepositoryPort;
import com.gole.api.notification.domain.model.NotificationCategory;
import com.gole.api.notification.domain.model.NotificationPreferences;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 알림 수신 설정 유스케이스 구현. (notification-preferences P2·P3) */
@Service
public class NotificationPreferenceService implements ManageNotificationPreferencesUseCase {

    private final NotificationPreferenceRepositoryPort repository;
    private final Clock clock;

    public NotificationPreferenceService(NotificationPreferenceRepositoryPort repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public NotificationPreferences get(String accountId) {
        return repository.find(accountId).orElseGet(() -> NotificationPreferences.defaults(accountId));
    }

    @Override
    public NotificationPreferences update(String accountId, Map<NotificationCategory, Boolean> changes) {
        NotificationPreferences next = get(accountId).update(changes, Instant.now(clock));
        return repository.save(next);
    }
}
