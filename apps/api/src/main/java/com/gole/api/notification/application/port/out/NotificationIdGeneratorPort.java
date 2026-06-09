package com.gole.api.notification.application.port.out;

/**
 * Outbound port: 알림 식별자 생성.
 */
public interface NotificationIdGeneratorPort {

    String newNotificationId();
}
