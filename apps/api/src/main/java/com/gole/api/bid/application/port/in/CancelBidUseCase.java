package com.gole.api.bid.application.port.in;

/** Inbound port: 내 입찰 취소. (buy-bids D4) */
public interface CancelBidUseCase {

    void cancel(String bidId, String actorId);
}
