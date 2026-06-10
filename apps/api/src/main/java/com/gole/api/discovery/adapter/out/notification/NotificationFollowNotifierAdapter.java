package com.gole.api.discovery.adapter.out.notification;

import com.gole.api.discovery.application.port.out.FollowNotifierPort;
import com.gole.api.notification.application.port.in.NotifyUseCase;
import com.gole.api.notification.application.port.in.NotifyUseCase.NotifyCommand;
import com.gole.api.notification.domain.model.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 팔로우 알림 어댑터. notification 인바운드 포트({@link NotifyUseCase})로 위임한다.
 * 알림 실패는 흡수해 팔로우 흐름을 막지 않는다(best-effort).
 */
@Component
public class NotificationFollowNotifierAdapter implements FollowNotifierPort {

    private static final Logger log = LoggerFactory.getLogger(NotificationFollowNotifierAdapter.class);

    private final NotifyUseCase notifyUseCase;

    public NotificationFollowNotifierAdapter(NotifyUseCase notifyUseCase) {
        this.notifyUseCase = notifyUseCase;
    }

    @Override
    public void notifyNewFollower(String sellerId, String followerId) {
        try {
            notifyUseCase.notify(
                    new NotifyCommand(sellerId, NotificationType.FOLLOW, "새 팔로워가 생겼어요", "/shops/" + sellerId));
        } catch (RuntimeException e) {
            log.warn("팔로우 알림 발송 실패 sellerId={} followerId={}: {}", sellerId, followerId, e.getMessage());
        }
    }
}
