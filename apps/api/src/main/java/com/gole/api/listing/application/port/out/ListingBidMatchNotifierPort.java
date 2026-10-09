package com.gole.api.listing.application.port.out;

/**
 * Outbound port: 세트 매물이 새로 나오거나 싸졌음을 그 세트·상태 입찰자에게 알린다. (buy-bids D8)
 *
 * <p>등록과 가격 인하 때 부른다. 구현은 장애를 흡수한다 — 매물 등록·수정을 실패시키지 않는다.
 *
 * @see com.gole.api.listing.application.service.ListingService
 */
public interface ListingBidMatchNotifierPort {

    /**
     * @param setNumber    연결된 카탈로그 세트. 없으면 부르지 않는다
     * @param conditionKey 매물 상태 키(new_sealed 등)
     */
    void listingAvailable(
            String listingId, String sellerId, String title, String setNumber, String conditionKey, long price);
}
