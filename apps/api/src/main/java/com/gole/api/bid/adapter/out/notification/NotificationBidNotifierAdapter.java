package com.gole.api.bid.adapter.out.notification;

import com.gole.api.bid.application.port.out.BidNotifierPort;
import com.gole.api.notification.application.port.in.NotifyUseCase;
import com.gole.api.notification.application.port.in.NotifyUseCase.NotifyCommand;
import com.gole.api.notification.domain.model.NotificationType;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 입찰 알림을 notification 컨텍스트에 위임한다. (buy-bids D7, D8)
 *
 * <p>멱등 키: 체결은 입찰당 한 번({@code bid-filled:{bidId}}), 매칭은 매물·가격당 한 번
 * ({@code bid-match:{listingId}:{price}}) — 같은 매물이 더 내려가면 다시 울린다. 발송 장애는 흡수한다.
 */
@Component
public class NotificationBidNotifierAdapter implements BidNotifierPort {

    private static final Logger log = LoggerFactory.getLogger(NotificationBidNotifierAdapter.class);

    private final NotifyUseCase notifications;

    public NotificationBidNotifierAdapter(NotifyUseCase notifications) {
        this.notifications = notifications;
    }

    @Override
    public void bidFilled(String bidderId, String bidId, String listingId, long price, String setLabel) {
        send(new NotifyCommand(
                bidderId,
                NotificationType.BID_FILLED,
                "판매자가 입찰가 " + won(price) + "에 " + setLabel + " 판매를 수락했어요. 72시간 안에 진행해 주세요",
                "/listings/" + listingId,
                "bid-filled:" + bidId));
    }

    @Override
    public void listingMatched(String bidderId, String listingId, String title, long price) {
        send(new NotifyCommand(
                bidderId,
                NotificationType.BID_LISTING_MATCHED,
                "입찰가 이하 매물이 올라왔어요: " + title + " " + won(price),
                "/listings/" + listingId,
                "bid-match:" + listingId + ":" + price));
    }

    private void send(NotifyCommand command) {
        try {
            notifications.notify(command);
        } catch (RuntimeException failure) {
            log.warn(
                    "입찰 알림 발송 실패 type={} recipientId={}: {}",
                    command.type(),
                    command.recipientId(),
                    failure.getMessage());
        }
    }

    private static String won(long amount) {
        return String.format(Locale.KOREA, "%,d원", amount);
    }
}
