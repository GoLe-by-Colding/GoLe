package com.gole.api.notification.adapter.out.id;

import com.gole.api.notification.application.port.out.NotificationIdGeneratorPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * UUID 기반 알림 식별자 생성 어댑터.
 */
@Component
public class UuidNotificationIdGenerator implements NotificationIdGeneratorPort {

    @Override
    public String newNotificationId() {
        return UUID.randomUUID().toString();
    }
}
