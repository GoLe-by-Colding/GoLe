package com.gole.api.listing.adapter.out.notification;

import com.gole.api.discovery.application.port.in.ListListingWishersUseCase;
import com.gole.api.listing.application.port.out.ListingPriceDropNotifierPort;
import com.gole.api.notification.application.port.in.NotifyUseCase;
import com.gole.api.notification.application.port.in.NotifyUseCase.NotifyCommand;
import com.gole.api.notification.domain.model.NotificationType;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 찜한 매물의 가격 인하 알림을 notification 컨텍스트에 위임한다. (listing-edit-and-bump E8, E9)
 *
 * <p>수신자는 discovery의 {@link ListListingWishersUseCase}로 해석하고 판매자 본인은 뺀다.
 * 멱등 키에 새 가격을 넣어 같은 인하의 재시도는 한 번만 울리고, 더 내리면 다시 울리게 한다.
 *
 * <p>수정 트랜잭션 안에서 불리면 커밋 뒤로 미룬다. 커밋 전에 보내면 수정이 되돌려져도 알림이
 * 남고, 알림 쓰기가 같은 Mongo 세션을 abort시키면 수정까지 실패한다. 조회·발송 장애는 모두 흡수한다.
 */
@Component
public class NotificationListingPriceDropNotifierAdapter implements ListingPriceDropNotifierPort {

    private static final Logger log = LoggerFactory.getLogger(NotificationListingPriceDropNotifierAdapter.class);

    private final ListListingWishersUseCase wishers;
    private final NotifyUseCase notifications;

    public NotificationListingPriceDropNotifierAdapter(ListListingWishersUseCase wishers, NotifyUseCase notifications) {
        this.wishers = wishers;
        this.notifications = notifications;
    }

    @Override
    public void priceDropped(String listingId, String sellerId, String title, long oldPrice, long newPrice) {
        Runnable delivery = () -> deliver(listingId, sellerId, title, oldPrice, newPrice);
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delivery.run();
                }
            });
            return;
        }
        delivery.run();
    }

    private void deliver(String listingId, String sellerId, String title, long oldPrice, long newPrice) {
        try {
            Set<String> recipients = new LinkedHashSet<>(wishers.wishersOf(listingId));
            recipients.remove(sellerId);
            String message = "찜한 매물 가격이 내려갔어요: " + title + " " + won(oldPrice) + " → " + won(newPrice);
            String deduplicationKey = "listing-price-drop:" + listingId + ":" + newPrice;
            for (String recipientId : recipients) {
                notifyOne(recipientId, listingId, message, deduplicationKey);
            }
        } catch (RuntimeException exception) {
            // 찜한 사람 조회 장애도 이미 끝난 수정을 되돌리면 안 된다.
            log.warn("가격 인하 알림 수신자 조회 실패 listingId={}: {}", listingId, exception.getMessage());
        }
    }

    private void notifyOne(String recipientId, String listingId, String message, String deduplicationKey) {
        try {
            notifications.notify(new NotifyCommand(
                    recipientId,
                    NotificationType.LISTING_PRICE_DROPPED,
                    message,
                    "/listings/" + listingId,
                    deduplicationKey));
        } catch (RuntimeException exception) {
            // 한 명의 알림 실패가 나머지 수신자를 막지 않는다.
            log.warn("가격 인하 알림 발송 실패 recipientId={} listingId={}: {}", recipientId, listingId, exception.getMessage());
        }
    }

    private static String won(long amount) {
        return String.format(Locale.KOREA, "%,d원", amount);
    }
}
