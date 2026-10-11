package com.gole.api.offer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.offer.domain.exception.OfferErrors;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PriceOfferTest {

    private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");
    private static final Duration PENDING_TTL = Duration.ofHours(48);
    private static final Duration ACCEPTED_TTL = Duration.ofHours(72);

    private static PriceOffer pending() {
        return PriceOffer.propose(
                "offer-1", "listing-1", "room-1", "buyer-1", "seller-1", 250_000, 280_000, T0, PENDING_TTL);
    }

    @Test
    @DisplayName("새 제안은 대기 상태이고 생성 시각 + 대기 TTL에 만료된다")
    void propose_createsPendingOfferExpiringAfterPendingTtl() {
        PriceOffer offer = pending();

        assertThat(offer.status()).isEqualTo(OfferStatus.PENDING);
        assertThat(offer.origin()).isEqualTo(OfferOrigin.CHAT);
        assertThat(offer.roomId()).isEqualTo("room-1");
        assertThat(offer.listingPriceAtOffer()).isEqualTo(280_000);
        assertThat(offer.respondedAt()).isNull();
        assertThat(offer.expiresAt()).isEqualTo(T0.plus(PENDING_TTL));
    }

    @Test
    @DisplayName("제안 가격은 0보다 크고 매물가보다 낮아야 한다")
    void propose_rejectsPriceOutsideOpenRange() {
        for (long price : new long[] {0, -1, 280_000, 300_000}) {
            assertThatThrownBy(() -> PriceOffer.propose(
                            "offer-1", "listing-1", "room-1", "buyer-1", "seller-1", price, 280_000, T0, PENDING_TTL))
                    .extracting("code")
                    .isEqualTo(OfferErrors.PRICE_INVALID);
        }
        assertThat(PriceOffer.propose(
                                "offer-1",
                                "listing-1",
                                "room-1",
                                "buyer-1",
                                "seller-1",
                                279_999,
                                280_000,
                                T0,
                                PENDING_TTL)
                        .price())
                .isEqualTo(279_999);
        assertThat(PriceOffer.propose(
                                "offer-1", "listing-1", "room-1", "buyer-1", "seller-1", 1, 280_000, T0, PENDING_TTL)
                        .price())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("판매자는 자기 매물에 제안할 수 없다")
    void propose_rejectsSellerAsBuyer() {
        assertThatThrownBy(() -> PriceOffer.propose(
                        "offer-1", "listing-1", "room-1", "seller-1", "seller-1", 250_000, 280_000, T0, PENDING_TTL))
                .extracting("code")
                .isEqualTo(OfferErrors.BUYER_ONLY);
    }

    @Test
    @DisplayName("대기 제안은 만료 시각 직전까지 대기, 만료 시각부터 만료다")
    void effectiveStatus_expiresExactlyAtExpiresAt() {
        PriceOffer offer = pending();
        Instant expiresAt = T0.plus(PENDING_TTL);

        assertThat(offer.effectiveStatus(expiresAt.minusNanos(1))).isEqualTo(OfferStatus.PENDING);
        assertThat(offer.effectiveStatus(expiresAt)).isEqualTo(OfferStatus.EXPIRED);
        assertThat(offer.asOf(expiresAt).status()).isEqualTo(OfferStatus.EXPIRED);
        assertThat(offer.asOf(expiresAt.minusNanos(1))).isSameAs(offer);
    }

    @Test
    @DisplayName("수락은 만료를 수락 시각 + 수락 TTL로 다시 잡는다")
    void accept_resetsExpiryToAcceptedTtl() {
        Instant acceptedAt = T0.plus(Duration.ofHours(47));

        PriceOffer accepted = pending().accept(acceptedAt, ACCEPTED_TTL);

        assertThat(accepted.status()).isEqualTo(OfferStatus.ACCEPTED);
        assertThat(accepted.respondedAt()).isEqualTo(acceptedAt);
        assertThat(accepted.expiresAt()).isEqualTo(acceptedAt.plus(ACCEPTED_TTL));
        assertThat(accepted.effectiveStatus(acceptedAt.plus(ACCEPTED_TTL).minusNanos(1)))
                .isEqualTo(OfferStatus.ACCEPTED);
        assertThat(accepted.effectiveStatus(acceptedAt.plus(ACCEPTED_TTL))).isEqualTo(OfferStatus.EXPIRED);
    }

    @Test
    @DisplayName("만료된 대기 제안이나 이미 수락된 제안은 수락할 수 없다")
    void accept_requiresEffectivePending() {
        assertThatThrownBy(() -> pending().accept(T0.plus(PENDING_TTL), ACCEPTED_TTL))
                .extracting("code")
                .isEqualTo(OfferErrors.NOT_PENDING);
        PriceOffer accepted = pending().accept(T0, ACCEPTED_TTL);
        assertThatThrownBy(() -> accepted.accept(T0, ACCEPTED_TTL))
                .extracting("code")
                .isEqualTo(OfferErrors.NOT_PENDING);
    }

    @Test
    @DisplayName("거절·철회는 대기와 수락 상태에서만 가능하다")
    void declineAndWithdraw_requireOpenStatus() {
        Instant later = T0.plusSeconds(60);
        assertThat(pending().decline(later).status()).isEqualTo(OfferStatus.DECLINED);
        assertThat(pending().decline(later).respondedAt()).isEqualTo(later);
        assertThat(pending().accept(T0, ACCEPTED_TTL).decline(later).status()).isEqualTo(OfferStatus.DECLINED);
        assertThat(pending().withdraw(later).status()).isEqualTo(OfferStatus.WITHDRAWN);
        assertThat(pending().accept(T0, ACCEPTED_TTL).withdraw(later).status()).isEqualTo(OfferStatus.WITHDRAWN);

        PriceOffer declined = pending().decline(later);
        assertThatThrownBy(() -> declined.withdraw(later)).extracting("code").isEqualTo(OfferErrors.NOT_OPEN);
        assertThatThrownBy(() -> declined.decline(later)).extracting("code").isEqualTo(OfferErrors.NOT_OPEN);
        assertThatThrownBy(() -> pending().withdraw(T0.plus(PENDING_TTL)))
                .extracting("code")
                .isEqualTo(OfferErrors.NOT_OPEN);
    }

    @Test
    @DisplayName("주문에는 이 매물·이 구매자의 유효 수락 제안만 쓸 수 있다")
    void isUsableFor_requiresSameListingBuyerAndEffectiveAccepted() {
        PriceOffer accepted = pending().accept(T0, ACCEPTED_TTL);

        assertThat(accepted.isUsableFor("listing-1", "buyer-1", T0)).isTrue();
        assertThat(accepted.isUsableFor("listing-2", "buyer-1", T0)).isFalse();
        assertThat(accepted.isUsableFor("listing-1", "buyer-2", T0)).isFalse();
        assertThat(accepted.isUsableFor("listing-1", "buyer-1", T0.plus(ACCEPTED_TTL)))
                .isFalse();
        assertThat(pending().isUsableFor("listing-1", "buyer-1", T0)).isFalse();
    }

    @Test
    @DisplayName("입찰에서 온 제안은 방 없이 바로 수락 상태이고 매물가 이상이어도 된다")
    void preAccepted_createsAcceptedOfferWithoutRoom() {
        PriceOffer offer = PriceOffer.preAccepted(
                "offer-1", "listing-1", "buyer-1", "seller-1", 300_000, 280_000, OfferOrigin.BID, T0, ACCEPTED_TTL);

        assertThat(offer.status()).isEqualTo(OfferStatus.ACCEPTED);
        assertThat(offer.roomId()).isNull();
        assertThat(offer.origin()).isEqualTo(OfferOrigin.BID);
        assertThat(offer.respondedAt()).isEqualTo(T0);
        assertThat(offer.expiresAt()).isEqualTo(T0.plus(ACCEPTED_TTL));
        assertThatThrownBy(() -> PriceOffer.preAccepted(
                        "offer-1", "listing-1", "buyer-1", "seller-1", 0, 280_000, OfferOrigin.BID, T0, ACCEPTED_TTL))
                .extracting("code")
                .isEqualTo(OfferErrors.PRICE_INVALID);
    }

    @Test
    @DisplayName("응답은 판매자만, 철회는 구매자만 할 수 있다")
    void roleGuards_rejectOtherParties() {
        PriceOffer offer = pending();

        offer.requireSeller("seller-1");
        offer.requireBuyer("buyer-1");
        assertThatThrownBy(() -> offer.requireSeller("buyer-1"))
                .extracting("code")
                .isEqualTo(OfferErrors.ACCESS_DENIED);
        assertThatThrownBy(() -> offer.requireBuyer("seller-1"))
                .extracting("code")
                .isEqualTo(OfferErrors.ACCESS_DENIED);
        assertThatThrownBy(() -> offer.requireSeller("stranger"))
                .extracting("code")
                .isEqualTo(OfferErrors.ACCESS_DENIED);
        assertThat(offer.isParty("buyer-1")).isTrue();
        assertThat(offer.isParty("stranger")).isFalse();
    }
}
