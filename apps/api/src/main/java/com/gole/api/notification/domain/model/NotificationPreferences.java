package com.gole.api.notification.domain.model;

import com.gole.api.common.exception.BadRequestException;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 계정 하나의 알림 수신 설정. 프레임워크 무의존. (notification-preferences P1·P3·P4)
 *
 * <p><b>꺼둔 분류만 기록한다.</b> 기본값이 "전부 켜짐"이므로, 새 분류가 생겨도 기존 사용자는 따로 옮기지
 * 않아도 켜진 채로 받는다. 끌 수 없는 분류({@link NotificationCategory#mandatory()})는 저장값에 섞여
 * 들어와도 무시한다 — 어떤 경로로든 거래 진행 알림이 꺼지는 일은 없어야 한다.
 */
public final class NotificationPreferences {

    private final String accountId;
    private final Set<NotificationCategory> disabled;
    private final Instant updatedAt; // nullable — 한 번도 저장하지 않은 기본값

    public NotificationPreferences(String accountId, Collection<NotificationCategory> disabled, Instant updatedAt) {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("accountId must not be blank");
        }
        this.accountId = accountId;
        EnumSet<NotificationCategory> optional = EnumSet.noneOf(NotificationCategory.class);
        if (disabled != null) {
            disabled.stream()
                    .filter(Objects::nonNull)
                    .filter(category -> !category.mandatory())
                    .forEach(optional::add);
        }
        this.disabled = Collections.unmodifiableSet(optional);
        this.updatedAt = updatedAt;
    }

    /** 저장된 설정이 없는 계정의 기본값: 전부 켜짐. */
    public static NotificationPreferences defaults(String accountId) {
        return new NotificationPreferences(accountId, Set.of(), null);
    }

    /** 이 종류의 알림을 받는가. */
    public boolean allows(NotificationType type) {
        return isEnabled(Objects.requireNonNull(type, "type").category());
    }

    /** 이 분류가 켜져 있는가. 끌 수 없는 분류는 항상 켜져 있다. */
    public boolean isEnabled(NotificationCategory category) {
        Objects.requireNonNull(category, "category");
        return category.mandatory() || !disabled.contains(category);
    }

    /**
     * 바꿀 분류만 받아 새 설정을 만든다. 빠진 분류는 그대로 둔다. (P3)
     *
     * <p>끌 수 없는 분류를 끄려 하면 거부한다. 켜는 요청({@code trade: true})은 이미 켜져 있으므로
     * 받아들인다 — 화면이 전체 상태를 그대로 되돌려 보내도 실패하지 않게 하기 위해서다.
     */
    public NotificationPreferences update(Map<NotificationCategory, Boolean> changes, Instant now) {
        Objects.requireNonNull(changes, "changes");
        EnumSet<NotificationCategory> next =
                disabled.isEmpty() ? EnumSet.noneOf(NotificationCategory.class) : EnumSet.copyOf(disabled);
        for (Map.Entry<NotificationCategory, Boolean> change : changes.entrySet()) {
            NotificationCategory category = Objects.requireNonNull(change.getKey(), "category");
            boolean enabled = Objects.requireNonNull(change.getValue(), "enabled");
            if (category.mandatory()) {
                if (!enabled) {
                    throw new BadRequestException(
                            "NOTIFICATION_CATEGORY_MANDATORY", "'" + category.label() + "' 알림은 끌 수 없습니다");
                }
                continue;
            }
            if (enabled) {
                next.remove(category);
            } else {
                next.add(category);
            }
        }
        return new NotificationPreferences(accountId, next, Objects.requireNonNull(now, "now"));
    }

    public String getAccountId() {
        return accountId;
    }

    /** 꺼둔 분류. 끌 수 없는 분류는 들어 있지 않다. */
    public Set<NotificationCategory> getDisabled() {
        return disabled;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
