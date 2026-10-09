package com.gole.api.offer.application.port.in;

import com.gole.api.offer.domain.model.PriceOffer;

/**
 * Inbound port: 제안 응답. 판매자는 수락·거절, 구매자는 철회한다. (price-offer O7~O11)
 *
 * <p>전이는 현재 상태를 조건으로 한 원자적 갱신이라 동시에 들어온 수락·철회 중 하나만 이긴다.
 * 반환 상태는 유효 상태다.
 */
public interface RespondToOfferUseCase {

    PriceOffer accept(String offerId, String actorId);

    PriceOffer decline(String offerId, String actorId);

    PriceOffer withdraw(String offerId, String actorId);
}
