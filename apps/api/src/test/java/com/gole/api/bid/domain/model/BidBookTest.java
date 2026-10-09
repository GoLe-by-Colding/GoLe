package com.gole.api.bid.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.bid.domain.model.BidBook.ConditionBook;
import com.gole.api.bid.domain.model.BidBook.Level;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 공개 호가창 계산. (buy-bids D6) */
class BidBookTest {

    private static final Instant T0 = Instant.parse("2026-03-11T00:00:00Z");

    private int seq = 0;

    private Bid bid(BidCondition condition, long price) {
        seq++;
        return Bid.place("bid-" + seq, "buyer-" + seq, "10307", condition, price, 30, T0);
    }

    @Test
    @DisplayName("상태 다섯 개를 좋은 상태부터 모두 담고, 입찰 없는 상태는 빈 칸이다")
    void book_listsAllConditionsInOrder() {
        BidBook book = BidBook.of("10307", List.of(bid(BidCondition.USED_GOOD, 150_000)), T0);

        assertThat(book.conditions()).extracting(ConditionBook::condition).containsExactly(BidCondition.values());
        ConditionBook empty = book.conditions().getFirst();
        assertThat(empty.highestPrice()).isNull();
        assertThat(empty.bidCount()).isZero();
        assertThat(empty.levels()).isEmpty();
    }

    @Test
    @DisplayName("같은 가격은 한 단으로 묶고, 높은 가격부터 5단까지만 보인다")
    void book_groupsLevelsAndKeepsTopFive() {
        List<Bid> bids = new ArrayList<>();
        for (long price : new long[] {100, 200, 300, 300, 400, 500, 600, 50}) {
            bids.add(bid(BidCondition.NEW_SEALED, price));
        }

        ConditionBook sealed = BidBook.of("10307", bids, T0).conditions().getFirst();

        assertThat(sealed.highestPrice()).isEqualTo(600);
        assertThat(sealed.bidCount()).isEqualTo(8);
        assertThat(sealed.levels())
                .containsExactly(
                        new Level(600, 1), new Level(500, 1), new Level(400, 1), new Level(300, 2), new Level(200, 1));
    }

    @Test
    @DisplayName("만료·취소·체결된 입찰과 다른 세트 입찰은 세지 않는다")
    void book_countsOnlyActiveBidsOfThatSet() {
        Bid expired = bid(BidCondition.LIKE_NEW, 900);
        Bid canceled = bid(BidCondition.LIKE_NEW, 800).cancel("buyer-" + seq, T0);
        Bid filled = bid(BidCondition.LIKE_NEW, 700).fill("listing-1", T0);
        Bid otherSet = Bid.place("bid-x", "buyer-x", "75192", BidCondition.LIKE_NEW, 1_000, 30, T0);
        Bid live = Bid.place("bid-live", "buyer-live", "10307", BidCondition.LIKE_NEW, 600, 60, T0);
        Instant now = T0.plus(Duration.ofDays(31)); // 30일 입찰은 만료, 60일 입찰은 살아 있다

        ConditionBook likeNew = BidBook.of("10307", List.of(expired, canceled, filled, otherSet, live), now)
                .conditions()
                .get(1);

        assertThat(likeNew.highestPrice()).isEqualTo(600);
        assertThat(likeNew.bidCount()).isEqualTo(1);
    }
}
