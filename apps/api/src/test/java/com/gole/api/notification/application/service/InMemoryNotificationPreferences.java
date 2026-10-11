package com.gole.api.notification.application.service;

import com.gole.api.notification.application.port.out.NotificationPreferenceRepositoryPort;
import com.gole.api.notification.domain.model.NotificationCategory;
import com.gole.api.notification.domain.model.NotificationPreferences;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** 수신 설정 테스트용 가짜 포트. {@link #failWith}로 저장소 장애를 흉내 낸다. */
class InMemoryNotificationPreferences implements NotificationPreferenceRepositoryPort {

    final Map<String, NotificationPreferences> rows = new HashMap<>();
    int findCalls;
    private RuntimeException failure;

    /** 이 계정이 분류들을 꺼둔 상태로 둔다. */
    InMemoryNotificationPreferences disable(String accountId, NotificationCategory... categories) {
        rows.put(accountId, new NotificationPreferences(accountId, List.of(categories), Instant.EPOCH));
        return this;
    }

    void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    @Override
    public Optional<NotificationPreferences> find(String accountId) {
        findCalls++;
        throwIfFailing();
        return Optional.ofNullable(rows.get(accountId));
    }

    @Override
    public NotificationPreferences save(NotificationPreferences preferences) {
        throwIfFailing();
        rows.put(preferences.getAccountId(), preferences);
        return preferences;
    }

    @Override
    public Set<String> findAccountsDisabling(NotificationCategory category, Collection<String> accountIds) {
        throwIfFailing();
        return accountIds.stream()
                .filter(id -> rows.containsKey(id) && !rows.get(id).isEnabled(category))
                .collect(Collectors.toSet());
    }

    private void throwIfFailing() {
        if (failure != null) {
            throw failure;
        }
    }
}
