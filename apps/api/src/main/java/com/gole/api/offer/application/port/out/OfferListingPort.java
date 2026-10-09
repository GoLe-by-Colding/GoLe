package com.gole.api.offer.application.port.out;

import java.util.Optional;

/**
 * Outbound port: 리스팅 컨텍스트 조회. 리스팅 도메인 모델 대신 제안에 필요한 최소 데이터만 받는다.
 */
public interface OfferListingPort {

    /** 매물이 없으면 비어 있다. */
    Optional<OfferListing> find(String listingId);

    /**
     * @param price 현재 매물가(원)
     * @param active 판매 중({@code ACTIVE})인가. 예약·판매·삭제된 매물은 false
     */
    record OfferListing(String listingId, String sellerId, long price, boolean active) {}
}
