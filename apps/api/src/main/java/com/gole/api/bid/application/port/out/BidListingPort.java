package com.gole.api.bid.application.port.out;

/** Outbound port: 판매자 즉시 판매에 쓸 매물 확인. listing 도메인을 필요한 최소 데이터로 환원한다. */
public interface BidListingPort {

    /** @throws com.gole.api.common.exception.NotFoundException 매물이 없을 때 */
    BidListing get(String listingId);

    /** 읽은 판매가가 그대로일 때만 입찰가로 올린다. 같은 Mongo 트랜잭션 안에서 호출한다. */
    void raisePriceTo(String listingId, String sellerId, long expectedPrice, long targetPrice);

    /**
     * @param setNumber    연결된 카탈로그 세트. 없으면 null
     * @param conditionKey 매물 상태 키(D1)
     */
    record BidListing(
            String listingId, String sellerId, String setNumber, String conditionKey, long price, boolean active) {}
}
