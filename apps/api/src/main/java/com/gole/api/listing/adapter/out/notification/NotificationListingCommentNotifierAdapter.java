package com.gole.api.listing.adapter.out.notification;

import com.gole.api.listing.application.port.out.ListingCommentNotifierPort;
import com.gole.api.notification.application.port.in.NotifyUseCase;
import com.gole.api.notification.application.port.in.NotifyUseCase.NotifyCommand;
import com.gole.api.notification.domain.model.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 매물 문의 알림을 notification 컨텍스트에 위임한다. 실패는 흡수한다 — 댓글 저장을 되돌리지 않는다. */
@Component
public class NotificationListingCommentNotifierAdapter implements ListingCommentNotifierPort {

    private static final Logger log = LoggerFactory.getLogger(NotificationListingCommentNotifierAdapter.class);
    private static final int TITLE_PREVIEW_LENGTH = 20;

    private final NotifyUseCase notifications;

    public NotificationListingCommentNotifierAdapter(NotifyUseCase notifications) {
        this.notifications = notifications;
    }

    @Override
    public void notifySellerOfQuestion(String sellerId, String listingId, String listingTitle) {
        try {
            notifications.notify(new NotifyCommand(
                    sellerId,
                    NotificationType.COMMENT,
                    "매물 '" + truncate(listingTitle) + "'에 문의가 달렸어요.",
                    "/listings/" + listingId));
        } catch (RuntimeException exception) {
            log.warn("매물 문의 알림 실패 sellerId={} listingId={}: {}", sellerId, listingId, exception.getMessage());
        }
    }

    private static String truncate(String title) {
        return title != null && title.length() > TITLE_PREVIEW_LENGTH
                ? title.substring(0, TITLE_PREVIEW_LENGTH) + "…"
                : title;
    }
}
