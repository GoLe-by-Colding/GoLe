package com.gole.api.order.adapter.out.offer;

import com.gole.api.offer.application.port.in.ResolveAcceptedOfferUseCase;
import com.gole.api.order.application.port.out.AcceptedOfferPort;
import java.time.Instant;
import java.util.OptionalLong;
import org.springframework.stereotype.Component;

/**
 * 제안 컨텍스트 통합 어댑터. 주문의 {@link AcceptedOfferPort}를 offer의 {@link ResolveAcceptedOfferUseCase}로
 * 위임한다. 의존 방향은 order → offer 하나뿐이다 — offer는 주문을 모른다. (price-offer Design)
 */
@Component
public class AcceptedOfferAdapter implements AcceptedOfferPort {

    private final ResolveAcceptedOfferUseCase resolveAcceptedOffer;

    public AcceptedOfferAdapter(ResolveAcceptedOfferUseCase resolveAcceptedOffer) {
        this.resolveAcceptedOffer = resolveAcceptedOffer;
    }

    @Override
    public OptionalLong usablePrice(String offerId, String listingId, String buyerId, Instant now) {
        return resolveAcceptedOffer.usablePrice(offerId, listingId, buyerId, now);
    }
}
