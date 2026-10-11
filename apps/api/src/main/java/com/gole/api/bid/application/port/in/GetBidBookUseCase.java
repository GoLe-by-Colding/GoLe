package com.gole.api.bid.application.port.in;

import com.gole.api.bid.domain.model.BidBook;

/** Inbound port: 세트 구매 호가창(공개). 입찰자 식별자는 내보내지 않는다. (buy-bids D6) */
public interface GetBidBookUseCase {

    BidBook book(String setNumber);
}
