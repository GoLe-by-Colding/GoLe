package com.gole.api.bid.application.port.out;

/** Outbound port: 판매자 즉시 판매에 쓸 매물 확인. listing 도메인을 필요한 최소 데이터로 환원한다. */
public interface BidListingPort {

    /** @throws com.gole.api.common.exception.NotFoundException 매물이 없을 때 */
    BidListing get(String listingId);

    /**
     * @param setNumber    연결된 카탈로그 세트. 없으면 null
     * @param conditionKey 매물 상태 키(D1)
     */
    record BidListing(
            String listingId, String sellerId, String setNumber, String conditionKey, long price, boolean active) {}
}
