package com.gole.api.offer.application.port.in;

import com.gole.api.offer.domain.model.OfferOrigin;
import com.gole.api.offer.domain.model.PriceOffer;

/**
 * Inbound port: 방 없이 바로 수락 상태인 제안 생성. 입찰 체결(buy-bids D7)이 쓴다. (price-offer O21)
 *
 * <p>매물은 그 판매자의 {@code ACTIVE} 매물이어야 한다. 가격은 매물가 이상이어도 된다 — 주문 금액은
 * {@code min(제안가, 매물가)}라서 매물가를 넘지 않는다. 알림은 호출한 쪽이 보낸다.
 */
public interface CreateAcceptedOfferUseCase {

    PriceOffer createAccepted(String listingId, String sellerId, String buyerId, long price, OfferOrigin origin);
}
