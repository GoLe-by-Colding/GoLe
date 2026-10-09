package com.gole.api.catalog.adapter.out.notification;

import com.gole.api.catalog.application.port.out.SetRetirementNotifierPort;
import com.gole.api.catalog.domain.model.RetirementStatus;
import com.gole.api.collection.application.port.in.ListSetHoldersUseCase;
import com.gole.api.collection.domain.model.OwnershipStatus;
import com.gole.api.discovery.application.port.in.ListSetWishersUseCase;
import com.gole.api.notification.application.port.in.NotifyUseCase;
import com.gole.api.notification.application.port.in.NotifyUseCase.NotifyCommand;
import com.gole.api.notification.domain.model.NotificationType;
import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 세트 단종 상태 변경을 관심·보유 사용자에게 알린다. 수신자는 discovery(위시리스트)와
 * collection(희망·보유)의 인바운드 포트로 해석하고, 발송은 notification에 위임한다.
 * (set-watch-alerts W2, W4, W5)
 */
@Component
public class NotificationSetRetirementNotifierAdapter implements SetRetirementNotifierPort {

    private static final Logger log = LoggerFactory.getLogger(NotificationSetRetirementNotifierAdapter.class);

    private final ListSetWishersUseCase wishers;
    private final ListSetHoldersUseCase holders;
    private final NotifyUseCase notifications;

    public NotificationSetRetirementNotifierAdapter(
            ListSetWishersUseCase wishers, ListSetHoldersUseCase holders, NotifyUseCase notifications) {
        this.wishers = wishers;
        this.holders = holders;
        this.notifications = notifications;
    }

    @Override
    public void retirementChanged(String setNumber, String setName, RetirementStatus status) {
        if (status == RetirementStatus.ACTIVE) {
            return;
        }
        try {
            Set<String> owners = status == RetirementStatus.RETIRED
                    ? new LinkedHashSet<>(holders.holdersOf(setNumber, OwnershipStatus.OWNED))
                    : Set.of();
            Set<String> watchers = new LinkedHashSet<>(wishers.wishersOf(setNumber));
            watchers.addAll(holders.holdersOf(setNumber, OwnershipStatus.WANTED));
            // 보유자이면서 관심 목록에도 있으면 보유자 문구 한 건만 보낸다.
            watchers.removeAll(owners);

            String link = "/sets/" + setNumber;
            String key = "set-retirement:" + setNumber + ":" + status.name();
            for (String ownerId : owners) {
                notifyOne(ownerId, "보유한 「" + setName + "」 단종됐어요. 지금 시세를 확인해 보세요", link, key);
            }
            String watcherMessage = status == RetirementStatus.RETIRING_SOON
                    ? "관심 세트 「" + setName + "」 곧 단종돼요. 지금 매물과 시세를 확인해 보세요"
                    : "관심 세트 「" + setName + "」 단종됐어요. 남은 매물을 확인해 보세요";
            for (String watcherId : watchers) {
                notifyOne(watcherId, watcherMessage, link, key);
            }
        } catch (RuntimeException exception) {
            // 수신자 조회 장애가 관리자의 세트 수정을 되돌리면 안 된다.
            log.warn("세트 단종 알림 수신자 조회 실패 setNumber={} status={}: {}", setNumber, status, exception.getMessage());
        }
    }

    private void notifyOne(String recipientId, String message, String link, String deduplicationKey) {
        try {
            notifications.notify(
                    new NotifyCommand(recipientId, NotificationType.SET_RETIREMENT, message, link, deduplicationKey));
        } catch (RuntimeException exception) {
            // 한 명의 실패가 나머지 수신자를 막지 않는다.
            log.warn("세트 단종 알림 발송 실패 recipientId={} link={}: {}", recipientId, link, exception.getMessage());
        }
    }
}
