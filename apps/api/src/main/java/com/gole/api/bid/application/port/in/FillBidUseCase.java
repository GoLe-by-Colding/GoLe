package com.gole.api.bid.application.port.in;

/**
 * Inbound port: 판매자가 자기 매물을 그 세트·상태의 최고 입찰가에 판다. (buy-bids D7)
 *
 * <p>체결은 결제가 아니라 입찰자에게 "수락된 가격 제안"을 만드는 것이다. 주문·결제는 기존 제안 경로를 탄다.
 */
public interface FillBidUseCase {

    FillResult fill(String setNumber, String listingId, String sellerId);

    /** 판매가를 체결 입찰가로 올리는 경우 가격 수정·체결·제안을 함께 확정한다. */
    FillResult fillWithPrice(String setNumber, String listingId, String sellerId, long targetPrice);

    record FillResult(String bidId, long bidPrice, String offerId, String listingId) {}
}
