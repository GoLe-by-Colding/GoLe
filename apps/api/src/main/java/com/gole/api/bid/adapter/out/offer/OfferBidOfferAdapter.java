package com.gole.api.bid.adapter.out.offer;

import com.gole.api.bid.application.port.out.BidOfferPort;
import com.gole.api.offer.application.port.in.CreateAcceptedOfferUseCase;
import com.gole.api.offer.domain.model.OfferOrigin;
import org.springframework.stereotype.Component;

/**
 * 제안 컨텍스트 통합 어댑터. 입찰 체결을 offer의 {@link CreateAcceptedOfferUseCase}로 위임한다 — 방 없이 바로
 * 수락 상태인 제안이 생기고, 입찰자는 그 제안으로 주문하거나 직거래한다. (buy-bids 설계 선택, price-offer O21)
 */
@Component
public class OfferBidOfferAdapter implements BidOfferPort {

    private final CreateAcceptedOfferUseCase createAcceptedOffer;

    public OfferBidOfferAdapter(CreateAcceptedOfferUseCase createAcceptedOffer) {
        this.createAcceptedOffer = createAcceptedOffer;
    }

    @Override
    public String createAccepted(String listingId, String sellerId, String bidderId, long price) {
        return createAcceptedOffer
                .createAccepted(listingId, sellerId, bidderId, price, OfferOrigin.BID)
                .id();
    }
}
