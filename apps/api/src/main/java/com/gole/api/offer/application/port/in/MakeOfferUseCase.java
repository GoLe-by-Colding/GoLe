package com.gole.api.offer.application.port.in;

import com.gole.api.offer.domain.model.PriceOffer;

/** Inbound port: 매물 채팅방 구매자의 가격 제안. (price-offer O1~O6) */
public interface MakeOfferUseCase {

    /** 대기 제안을 만들고 방 메시지·판매자 알림을 남긴다. 반환 상태는 유효 상태다. */
    PriceOffer make(MakeOfferCommand command);

    /**
     * @param roomId 매물 채팅방
     * @param buyerId 요청자. 그 방의 구매자여야 한다
     * @param price 제안 가격(원)
     */
    record MakeOfferCommand(String roomId, String buyerId, long price) {}
}
