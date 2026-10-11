package com.gole.api.order.application.port.out;

import java.time.Instant;
import java.util.OptionalLong;

/**
 * Outbound port: 주문 금액을 정할 수락 제안의 가격 확인. (price-offer O16)
 *
 * <p>주문은 제안 컨텍스트를 모른다. 이 포트로 "이 매물·이 구매자에게 지금 쓸 수 있는 제안가"만 묻는다.
 */
public interface AcceptedOfferPort {

    /** @return 쓸 수 있는 수락 제안이면 제안가, 아니면 비어 있음 */
    OptionalLong usablePrice(String offerId, String listingId, String buyerId, Instant now);
}
