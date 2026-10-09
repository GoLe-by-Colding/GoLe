package com.gole.api.offer.application.port.in;

import com.gole.api.offer.domain.model.PriceOffer;
import java.util.List;

/** Inbound port: 제안 조회. 결과는 최신순 최대 50건이고 상태는 유효 상태다. (price-offer O13, O14) */
public interface ListOffersUseCase {

    int MAX_RESULTS = 50;

    /** 그 방 참여자만 볼 수 있다. */
    List<PriceOffer> byRoom(String roomId, String actorId);

    /** 판매자면 그 매물의 모든 제안, 아니면 내 제안만. */
    List<PriceOffer> byListing(String listingId, String actorId);
}
