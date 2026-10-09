package com.gole.api.offer.application.port.in;

import java.time.Instant;
import java.util.OptionalLong;

/**
 * Inbound port: 주문이 쓸 수락 제안의 가격 확인. (price-offer O16)
 *
 * <p>제안을 소모하지 않는다(O18). 결제 대기 만료로 예약이 풀리면 같은 제안으로 다시 주문할 수 있고,
 * 이중 구매는 매물 예약이 막는다.
 */
public interface ResolveAcceptedOfferUseCase {

    /**
     * @return 제안이 이 매물·이 구매자의 유효 {@code ACCEPTED}이면 제안가, 아니면 비어 있음
     */
    OptionalLong usablePrice(String offerId, String listingId, String buyerId, Instant now);
}
