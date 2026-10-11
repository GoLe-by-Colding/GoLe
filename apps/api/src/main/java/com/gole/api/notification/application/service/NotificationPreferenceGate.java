package com.gole.api.notification.application.service;

import com.gole.api.notification.application.port.out.NotificationPreferenceRepositoryPort;
import com.gole.api.notification.domain.model.NotificationCategory;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 발송 경로가 수신 설정을 묻는 단일 창구. (notification-preferences P4·P5·P6)
 *
 * <p><b>조회가 실패하면 보낸다(fail-open).</b> 설정 저장소가 흔들릴 때 알림이 통째로 사라지는 것보다
 * 꺼둔 사람에게 한 번 더 울리는 쪽이 낫다. 실패는 경고 로그로만 남긴다.
 *
 * <p>끌 수 없는 분류는 저장소를 아예 묻지 않는다 — 주문·결제 알림 경로에 조회 하나를 더 얹을 이유가 없다.
 */
@Component
public class NotificationPreferenceGate {

    private static final Logger log = LoggerFactory.getLogger(NotificationPreferenceGate.class);

    private final NotificationPreferenceRepositoryPort preferences;

    public NotificationPreferenceGate(NotificationPreferenceRepositoryPort preferences) {
        this.preferences = preferences;
    }

    /** 이 계정이 이 분류의 알림을 받는가. */
    public boolean allows(String accountId, NotificationCategory category) {
        Objects.requireNonNull(category, "category");
        if (category.mandatory()) {
            return true;
        }
        try {
            return preferences
                    .find(accountId)
                    .map(found -> found.isEnabled(category))
                    .orElse(true);
        } catch (RuntimeException failure) {
            log.warn("알림 수신 설정 조회 실패 — 설정과 무관하게 보낸다 accountId={}, category={}", accountId, category, failure);
            return true;
        }
    }

    /** 주어진 계정 가운데 이 분류를 꺼둔 계정. 조회가 실패하면 아무도 거르지 않는다. */
    public Set<String> optedOut(NotificationCategory category, Collection<String> accountIds) {
        Objects.requireNonNull(category, "category");
        if (category.mandatory() || accountIds == null || accountIds.isEmpty()) {
            return Set.of();
        }
        try {
            return preferences.findAccountsDisabling(category, accountIds);
        } catch (RuntimeException failure) {
            log.warn(
                    "알림 수신 설정 일괄 조회 실패 — 이 묶음은 거르지 않는다 category={}, accounts={}", category, accountIds.size(), failure);
            return Set.of();
        }
    }
}
