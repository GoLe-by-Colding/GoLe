package com.gole.api.listing.adapter.out.bid;

import com.gole.api.bid.application.port.in.NotifyMatchingBidsUseCase;
import com.gole.api.bid.application.port.in.NotifyMatchingBidsUseCase.ListingAvailable;
import com.gole.api.listing.application.port.out.ListingBidMatchNotifierPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 입찰 매칭 알림을 bid의 {@link NotifyMatchingBidsUseCase}로 위임한다. (buy-bids D8)
 *
 * <p>listing → bid 방향은 이 어댑터 하나뿐이고, 상대 구현은 입찰 저장소·알림에만 의존해 순환이 생기지 않는다.
 * 등록·수정 트랜잭션 안에서 불리면 커밋 뒤로 미룬다 — 가격 인하 알림 어댑터와 같은 이유다(되돌려진 매물로 알림이
 * 나가지 않게, 알림 쓰기가 같은 Mongo 세션을 abort시키지 않게).
 */
@Component
public class BidMatchListingNotifierAdapter implements ListingBidMatchNotifierPort {

    private static final Logger log = LoggerFactory.getLogger(BidMatchListingNotifierAdapter.class);

    private final NotifyMatchingBidsUseCase matchingBids;

    public BidMatchListingNotifierAdapter(NotifyMatchingBidsUseCase matchingBids) {
        this.matchingBids = matchingBids;
    }

    @Override
    public void listingAvailable(
            String listingId, String sellerId, String title, String setNumber, String conditionKey, long price) {
        ListingAvailable event = new ListingAvailable(listingId, sellerId, title, setNumber, conditionKey, price);
        Runnable delivery = () -> deliver(event);
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

    private void deliver(ListingAvailable event) {
        try {
            matchingBids.listingAvailable(event);
        } catch (RuntimeException failure) {
            log.warn("입찰 매칭 알림 실패 listingId={}: {}", event.listingId(), failure.getMessage());
        }
    }
}
