package com.gole.api.offer.application.port.out;

import com.gole.api.offer.domain.model.PriceOffer;

/** Outbound port: 제안 알림. 실패는 호출한 쪽이 흡수한다 — 제안 상태가 원장이다. (price-offer O6~O8) */
public interface OfferNotifierPort {

    /** 판매자에게 새 제안. */
    void offerReceived(PriceOffer offer);

    /** 구매자에게 수락. */
    void offerAccepted(PriceOffer offer);

    /**
     * 구매자에게 거절.
     *
     * @param acceptanceCanceled 수락했던 제안을 판매자가 취소한 것인가
     */
    void offerDeclined(PriceOffer offer, boolean acceptanceCanceled);
}
