package com.gole.api.notification.adapter.in.web;

import com.gole.api.notification.application.port.in.GetNotificationsUseCase;
import com.gole.api.notification.domain.model.Notification;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound 어댑터(REST): 사용자 알림 조회/읽음. (알림 스펙 N2~N5)
 */
@Tag(name = "Notification", description = "알림 목록·읽음 처리")
@RestController
@RequestMapping("/api/v1/users/{userId}/notifications")
public class NotificationController {

    private final GetNotificationsUseCase getNotifications;

    public NotificationController(GetNotificationsUseCase getNotifications) {
        this.getNotifications = getNotifications;
    }

    @GetMapping
    public List<NotificationResponse> list(@PathVariable String userId) {
        return getNotifications.list(userId).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(@PathVariable String userId) {
        return new UnreadCountResponse(getNotifications.unreadCount(userId));
    }

    @PostMapping("/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable String userId, @PathVariable String notificationId) {
        getNotifications.markRead(notificationId, userId);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@PathVariable String userId) {
        getNotifications.markAllRead(userId);
    }

    public record NotificationResponse(
            String id, String type, String message, String link, boolean read, Instant createdAt) {

        static NotificationResponse from(Notification n) {
            return new NotificationResponse(
                    n.getId(), n.getType().name(), n.getMessage(), n.getLink(), n.isRead(), n.getCreatedAt());
        }
    }

    public record UnreadCountResponse(long unreadCount) {}
}
