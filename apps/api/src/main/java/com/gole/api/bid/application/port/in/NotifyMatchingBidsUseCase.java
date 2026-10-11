package com.gole.api.bid.application.port.in;

/**
 * Inbound port: 입찰가 이하 매물이 나왔음을 입찰자에게 알린다. (buy-bids D8)
 *
 * <p>listing이 등록·가격 인하 때 부른다. 순환을 피하려고 이 포트의 구현은 입찰 저장소와 알림에만 의존한다 —
 * listing·offer를 아는 {@code BidService}와 분리돼 있다.
 */
public interface NotifyMatchingBidsUseCase {

    int MAX_RECIPIENTS = 200;

    void listingAvailable(ListingAvailable listing);

    /** @param conditionKey 매물 상태 키(D1). 모르는 키면 아무에게도 보내지 않는다 */
    record ListingAvailable(
            String listingId, String sellerId, String title, String setNumber, String conditionKey, long price) {}
}
