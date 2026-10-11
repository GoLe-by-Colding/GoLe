package com.gole.api.offer.adapter.out.notification;

import com.gole.api.notification.application.port.in.NotifyUseCase;
import com.gole.api.notification.application.port.in.NotifyUseCase.NotifyCommand;
import com.gole.api.notification.domain.model.NotificationType;
import com.gole.api.offer.application.port.out.OfferNotifierPort;
import com.gole.api.offer.domain.model.PriceOffer;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * 제안 알림을 notification 컨텍스트에 위임한다. (price-offer O6~O8)
 *
 * <p>중복 키를 제안마다 고정해 같은 사건이 재시도로 두 번 알려지지 않게 한다. 실패 흡수는 서비스가 한다.
 */
@Component
public class NotificationOfferNotifierAdapter implements OfferNotifierPort {

    private final NotifyUseCase notifications;

    public NotificationOfferNotifierAdapter(NotifyUseCase notifications) {
        this.notifications = notifications;
    }

    @Override
    public void offerReceived(PriceOffer offer) {
        notifications.notify(new NotifyCommand(
                offer.sellerId(),
                NotificationType.OFFER_RECEIVED,
                "%s원 가격 제안이 도착했어요".formatted(won(offer.price())),
                "/chat?room=" + offer.roomId(),
                "offer-received:" + offer.id()));
    }

    @Override
    public void offerAccepted(PriceOffer offer) {
        notifications.notify(new NotifyCommand(
                offer.buyerId(),
                NotificationType.OFFER_ACCEPTED,
                "판매자가 %s원 제안을 수락했어요. 제안가로 구매를 진행해 보세요".formatted(won(offer.price())),
                "/listings/" + offer.listingId(),
                "offer-accepted:" + offer.id()));
    }

    @Override
    public void offerDeclined(PriceOffer offer, boolean acceptanceCanceled) {
        String message = acceptanceCanceled
                ? "판매자가 %s원 제안 수락을 취소했어요".formatted(won(offer.price()))
                : "판매자가 %s원 제안을 거절했어요".formatted(won(offer.price()));
        String link = offer.roomId() == null ? "/listings/" + offer.listingId() : "/chat?room=" + offer.roomId();
        notifications.notify(new NotifyCommand(
                offer.buyerId(), NotificationType.OFFER_DECLINED, message, link, "offer-declined:" + offer.id()));
    }

    private static String won(long amount) {
        return String.format(Locale.ROOT, "%,d", amount);
    }
}
