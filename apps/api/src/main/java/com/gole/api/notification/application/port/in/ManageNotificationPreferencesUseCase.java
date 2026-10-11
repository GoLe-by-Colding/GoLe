package com.gole.api.notification.application.port.in;

import com.gole.api.notification.domain.model.NotificationCategory;
import com.gole.api.notification.domain.model.NotificationPreferences;
import java.util.Map;

/** Inbound port: 내 알림 수신 설정 조회·변경. (notification-preferences P2·P3) */
public interface ManageNotificationPreferencesUseCase {

    /** 저장된 설정이 없으면 기본값(전부 켜짐)을 돌려준다. */
    NotificationPreferences get(String accountId);

    /**
     * 받은 분류만 바꾸고 나머지는 그대로 둔다.
     *
     * @throws com.gole.api.common.exception.BadRequestException 끌 수 없는 분류를 끄려 할 때
     *     ({@code NOTIFICATION_CATEGORY_MANDATORY})
     */
    NotificationPreferences update(String accountId, Map<NotificationCategory, Boolean> changes);
}
