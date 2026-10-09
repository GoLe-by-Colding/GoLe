package com.gole.api.bid.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.bid.domain.exception.BidErrors;
import com.gole.api.common.exception.DomainException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 입찰 도메인 규칙. (buy-bids D1~D4, D7, D9) */
class BidTest {

    private static final Instant T0 = Instant.parse("2026-03-11T00:00:00Z");

    private static Bid placed(long price, int days) {
        return Bid.place("bid-1", "buyer-1", "10307", BidCondition.NEW_SEALED, price, days, T0);
    }

    private static String codeOf(Runnable action) {
        try {
            action.run();
        } catch (DomainException error) {
            return error.getCode();
        }
        throw new AssertionError("예외가 나야 한다");
    }

    @Test
    @DisplayName("새 입찰은 ACTIVE이고 만료는 건 시각 + 기간이다")
    void place_startsActiveAndExpiresAfterDuration() {
        Bid bid = placed(250_000, 30);

        assertThat(bid.status()).isEqualTo(BidStatus.ACTIVE);
        assertThat(bid.createdAt()).isEqualTo(T0);
        assertThat(bid.placedAt()).isEqualTo(T0);
        assertThat(bid.expiresAt()).isEqualTo(T0.plus(Duration.ofDays(30)));
    }

    @Test
    @DisplayName("가격은 1원~1억 원, 기간은 7·30·60일만 받는다")
    void place_validatesPriceAndDuration() {
        assertThat(codeOf(() -> placed(0, 30))).isEqualTo(BidErrors.PRICE_INVALID);
        assertThat(codeOf(() -> placed(100_000_001, 30))).isEqualTo(BidErrors.PRICE_INVALID);
        assertThat(codeOf(() -> placed(1_000, 14))).isEqualTo(BidErrors.DURATION_INVALID);
        assertThat(placed(1, 7).price()).isEqualTo(1);
        assertThat(placed(100_000_000, 60).durationDays()).isEqualTo(60);
    }

    @Test
    @DisplayName("만료 시각 그 순간부터 유효 상태가 EXPIRED다")
    void effectiveStatus_expiresAtBoundary() {
        Bid bid = placed(250_000, 7);
        Instant expiry = T0.plus(Duration.ofDays(7));

        assertThat(bid.effectiveStatus(expiry.minusMillis(1))).isEqualTo(BidStatus.ACTIVE);
        assertThat(bid.effectiveStatus(expiry)).isEqualTo(BidStatus.EXPIRED);
        assertThat(bid.asOf(expiry).status()).isEqualTo(BidStatus.EXPIRED);
        assertThat(bid.asOf(expiry.minusMillis(1))).isSameAs(bid);
    }

    @Test
    @DisplayName("다시 걸면 가격·기간·만료만 바뀌고 생성 시각은 그대로다")
    void replace_keepsCreatedAtAndMovesExpiry() {
        Instant later = T0.plus(Duration.ofDays(3));

        Bid replaced = placed(250_000, 30).replace(260_000, 7, later);

        assertThat(replaced.id()).isEqualTo("bid-1");
        assertThat(replaced.price()).isEqualTo(260_000);
        assertThat(replaced.createdAt()).isEqualTo(T0);
        assertThat(replaced.placedAt()).isEqualTo(later);
        assertThat(replaced.expiresAt()).isEqualTo(later.plus(Duration.ofDays(7)));
    }

    @Test
    @DisplayName("취소는 입찰자 본인만, 진행 중일 때만 된다")
    void cancel_requiresBidderAndActive() {
        Bid bid = placed(250_000, 30);

        assertThat(codeOf(() -> bid.cancel("intruder", T0))).isEqualTo(BidErrors.ACCESS_DENIED);
        Bid canceled = bid.cancel("buyer-1", T0.plusSeconds(1));
        assertThat(canceled.status()).isEqualTo(BidStatus.CANCELED);
        assertThat(canceled.closedAt()).isEqualTo(T0.plusSeconds(1));
        assertThat(codeOf(() -> canceled.cancel("buyer-1", T0.plusSeconds(2)))).isEqualTo(BidErrors.NOT_ACTIVE);
        assertThat(codeOf(() -> bid.cancel("buyer-1", T0.plus(Duration.ofDays(30)))))
                .isEqualTo(BidErrors.NOT_ACTIVE);
    }

    @Test
    @DisplayName("체결하면 매물을 남기고, 제안을 붙이거나 되돌릴 수 있다")
    void fill_recordsListingAndSupportsOfferAndReopen() {
        Bid bid = placed(250_000, 30);

        Bid filled = bid.fill("listing-1", T0.plusSeconds(5));
        assertThat(filled.status()).isEqualTo(BidStatus.FILLED);
        assertThat(filled.filledListingId()).isEqualTo("listing-1");
        assertThat(filled.withOffer("offer-1").offerId()).isEqualTo("offer-1");

        Bid reopened = filled.reopen();
        assertThat(reopened.status()).isEqualTo(BidStatus.ACTIVE);
        assertThat(reopened.filledListingId()).isNull();
        assertThat(reopened.expiresAt()).isEqualTo(bid.expiresAt());

        assertThat(codeOf(() -> filled.fill("listing-2", T0.plusSeconds(6)))).isEqualTo(BidErrors.NOT_ACTIVE);
        assertThatThrownBy(() -> bid.withOffer("offer-1")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("상태 키는 소문자 키·열거형 이름 모두 받고 모르는 값은 비어 있다")
    void condition_parsesKeysStrictly() {
        assertThat(BidCondition.fromKey("like_new")).contains(BidCondition.LIKE_NEW);
        assertThat(BidCondition.fromKey("USED_FAIR")).contains(BidCondition.USED_FAIR);
        assertThat(BidCondition.fromKey(" damaged ")).contains(BidCondition.DAMAGED);
        assertThat(BidCondition.fromKey("used_complete")).isEmpty();
        assertThat(BidCondition.fromKey(null)).isEmpty();
    }
}
