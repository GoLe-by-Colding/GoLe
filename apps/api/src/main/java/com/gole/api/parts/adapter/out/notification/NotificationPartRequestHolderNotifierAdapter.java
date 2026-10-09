package com.gole.api.parts.adapter.out.notification;

import com.gole.api.notification.application.port.in.NotifyUseCase;
import com.gole.api.notification.application.port.in.NotifyUseCase.NotifyCommand;
import com.gole.api.notification.domain.model.NotificationType;
import com.gole.api.parts.application.port.out.PartRequestHolderNotifierPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 세트 보유자 부품 요청 알림을 notification 컨텍스트에 위임한다. (wanted-parts W8)
 *
 * <p>수신 설정(COMMUNITY 분류) 판정은 notification이 알림 종류로 한다. 중복 키를 요청 id로 고정해
 * 같은 요청으로 같은 사람에게 두 번 가지 않게 한다.
 */
@Component
public class NotificationPartRequestHolderNotifierAdapter implements PartRequestHolderNotifierPort {

    private static final Logger log = LoggerFactory.getLogger(NotificationPartRequestHolderNotifierAdapter.class);

    private final NotifyUseCase notifications;

    public NotificationPartRequestHolderNotifierAdapter(NotifyUseCase notifications) {
        this.notifications = notifications;
    }

    @Override
    public void notifyOwner(String recipientId, String requestId, String setNumber) {
        try {
            notifications.notify(new NotifyCommand(
                    recipientId,
                    NotificationType.PART_REQUEST_FOR_OWNED_SET,
                    "보유한 " + setNumber + " 세트의 부품을 찾는 요청이 있어요",
                    "/parts/" + requestId,
                    "part-request:" + requestId));
        } catch (RuntimeException exception) {
            // 한 명의 알림 실패가 나머지 보유자나 요청 등록을 막지 않는다.
            log.warn(
                    "부품 요청 보유자 알림 발송 실패 recipientId={} requestId={}: {}",
                    recipientId,
                    requestId,
                    exception.getMessage());
        }
    }
}
