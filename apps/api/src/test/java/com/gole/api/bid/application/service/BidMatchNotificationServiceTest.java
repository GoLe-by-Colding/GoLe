package com.gole.api.bid.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.gole.api.bid.application.port.in.NotifyMatchingBidsUseCase;
import com.gole.api.bid.application.port.in.NotifyMatchingBidsUseCase.ListingAvailable;
import com.gole.api.bid.application.port.out.BidNotifierPort;
import com.gole.api.bid.domain.model.Bid;
import com.gole.api.bid.domain.model.BidCondition;
import com.gole.api.bid.domain.model.BidStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 입찰가 이하 매물 알림. (buy-bids D8) */
class BidMatchNotificationServiceTest {

    private static final Instant T0 = Instant.parse("2026-03-11T00:00:00Z");

    private InMemoryBidRepository bids;
    private List<String> sent;
    private BidMatchNotificationService service;

    @BeforeEach
    void setUp() {
        bids = new InMemoryBidRepository();
        sent = new ArrayList<>();
        BidNotifierPort notifier = new BidNotifierPort() {
            @Override
            public void bidFilled(String bidderId, String bidId, String listingId, long price, String setLabel) {}

            @Override
            public void listingMatched(String bidderId, String listingId, String title, long price) {
                sent.add(bidderId + "|" + listingId + "|" + title + "|" + price);
            }
        };
        service = new BidMatchNotificationService(bids, notifier, Clock.fixed(T0, ZoneOffset.UTC));
    }

    private void bid(String bidder, BidCondition condition, long price) {
        bids.insert(Bid.place("bid-" + bidder + condition, bidder, "10307", condition, price, 30, T0.minusSeconds(60)));
    }

    private static ListingAvailable listing(String condition, long price) {
        return new ListingAvailable("listing-1", "seller-1", "에펠탑 미개봉", "10307", condition, price);
    }

    @Test
    @DisplayName("같은 세트·상태에서 입찰가가 매물가 이상인 입찰자에게만 보낸다")
    void notifiesBiddersAtOrAbovePrice() {
        bid("buyer-high", BidCondition.NEW_SEALED, 300_000);
        bid("buyer-equal", BidCondition.NEW_SEALED, 280_000);
        bid("buyer-low", BidCondition.NEW_SEALED, 279_999);
        bid("buyer-other-condition", BidCondition.LIKE_NEW, 400_000);

        service.listingAvailable(listing("new_sealed", 280_000));

        assertThat(sent)
                .containsExactlyInAnyOrder(
                        "buyer-high|listing-1|에펠탑 미개봉|280000", "buyer-equal|listing-1|에펠탑 미개봉|280000");
    }

    @Test
    @DisplayName("판매자 본인 입찰, 취소된 입찰에는 보내지 않는다")
    void skipsSellerAndInactiveBids() {
        bid("seller-1", BidCondition.NEW_SEALED, 300_000);
        bid("buyer-canceled", BidCondition.NEW_SEALED, 300_000);
        Bid canceled = bids.store.get("bid-buyer-canceled" + BidCondition.NEW_SEALED);
        bids.store.put(canceled.id(), canceled.cancel("buyer-canceled", T0));

        service.listingAvailable(listing("new_sealed", 280_000));

        assertThat(sent).isEmpty();
        assertThat(bids.store.get(canceled.id()).status()).isEqualTo(BidStatus.CANCELED);
    }

    @Test
    @DisplayName("수신자는 최대 200명이다 — 판매자를 뺀 뒤에 센다")
    void capsRecipients() {
        bid("seller-1", BidCondition.USED_GOOD, 999_999);
        for (int i = 0; i < 250; i++) {
            bid("buyer-" + i, BidCondition.USED_GOOD, 200_000 + i);
        }

        service.listingAvailable(listing("used_good", 100_000));

        assertThat(sent).hasSize(NotifyMatchingBidsUseCase.MAX_RECIPIENTS);
        assertThat(sent).noneMatch(line -> line.startsWith("seller-1|"));
    }

    @Test
    @DisplayName("세트가 없거나 상태 키를 모르면 아무 일도 하지 않고, 조회 장애는 흡수한다")
    void ignoresUnknownAndAbsorbsFailures() {
        bid("buyer-1", BidCondition.NEW_SEALED, 300_000);

        service.listingAvailable(new ListingAvailable("l", "s", "t", null, "new_sealed", 1));
        service.listingAvailable(listing("mint", 1));
        assertThat(sent).isEmpty();

        BidMatchNotificationService broken = new BidMatchNotificationService(
                new InMemoryBidRepository() {
                    @Override
                    public List<String> findBiddersAtOrAbove(
                            String setNumber, BidCondition condition, long price, Instant now, int limit) {
                        throw new IllegalStateException("mongo down");
                    }
                },
                null,
                Clock.fixed(T0, ZoneOffset.UTC));
        assertThatCode(() -> broken.listingAvailable(listing("new_sealed", 1))).doesNotThrowAnyException();
    }
}
