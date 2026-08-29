package com.gole.api.listing.adapter.out.notification;

import com.gole.api.discovery.application.port.in.ListSellerFollowersUseCase;
import com.gole.api.listing.application.port.out.NewListingNotifierPort;
import com.gole.api.notification.application.port.in.NotifyUseCase;
import com.gole.api.notification.application.port.in.NotifyUseCase.NotifyCommand;
import com.gole.api.notification.domain.model.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 팔로우한 셀러의 새 매물 알림을 notification 컨텍스트에 위임한다. */
@Component
public class NotificationNewListingNotifierAdapter implements NewListingNotifierPort {

    private static final Logger log = LoggerFactory.getLogger(NotificationNewListingNotifierAdapter.class);

    private final ListSellerFollowersUseCase followers;
    private final NotifyUseCase notifications;

    public NotificationNewListingNotifierAdapter(ListSellerFollowersUseCase followers, NotifyUseCase notifications) {
        this.followers = followers;
        this.notifications = notifications;
    }

    @Override
    public void notifyFollowers(String sellerId, String listingId, String title) {
        try {
            for (String recipientId : followers.followersOf(sellerId)) {
                notifyOne(recipientId, listingId, title);
            }
        } catch (RuntimeException exception) {
            // 팔로워 조회 장애도 매물 등록을 되돌리면 안 된다.
            log.warn("새 매물 팔로워 조회 실패 sellerId={} listingId={}: {}", sellerId, listingId, exception.getMessage());
        }
    }

    private void notifyOne(String recipientId, String listingId, String title) {
        try {
            notifications.notify(new NotifyCommand(
                    recipientId, NotificationType.NEW_LISTING, "팔로우한 셀러의 새 매물: " + title, "/listings/" + listingId));
        } catch (RuntimeException exception) {
            // 한 명의 알림 실패가 나머지 팔로워나 매물 등록을 막지 않는다.
            log.warn("새 매물 알림 발송 실패 recipientId={} listingId={}: {}", recipientId, listingId, exception.getMessage());
        }
    }
}
