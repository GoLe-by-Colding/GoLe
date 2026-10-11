package com.gole.api.bid.application.port.in;

import com.gole.api.bid.domain.model.Bid;

/** Inbound port: 입찰 걸기. 같은 세트·상태의 유효 입찰이 있으면 가격·만료를 갱신한다. (buy-bids D2, D3) */
public interface PlaceBidUseCase {

    Bid place(PlaceBidCommand command);

    /**
     * @param condition    상태 키(D1). 모르는 키는 {@code BID_CONDITION_INVALID}
     * @param durationDays 7·30·60 중 하나. null이면 30
     */
    record PlaceBidCommand(String bidderId, String setNumber, String condition, long price, Integer durationDays) {}
}
