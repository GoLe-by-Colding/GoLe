package com.gole.api.order.application.port.out;

import java.util.Optional;

/**
 * Outbound port: 리스팅 컨텍스트와의 통합. 주문 생성을 위한 원자적 선점 및
 * 결제 실패/환불 시 해제, 완료 시 판매 처리. (요구사항 13.1)
 *
 * <p>리스팅 도메인 모델에 직접 의존하지 않도록, 선점 결과는 주문 컨텍스트가
 * 필요로 하는 최소 데이터({@link ReservedListing})로 환원해 반환한다.
 */
public interface ListingReservationPort {

    /** ACTIVE → RESERVED 원자적 선점. 활성이 아니면 비어있음을 반환한다. */
    Optional<ReservedListing> reserve(String listingId);

    /** 선점 해제(RESERVED → ACTIVE). 결제 실패/환불 시. */
    void release(String listingId);

    /** 판매 완료 처리. 주문 완료 시. */
    void markSold(String listingId);

    /**
     * 선점된 리스팅의 주문 생성에 필요한 데이터.
     *
     * @param listingId 리스팅 식별자
     * @param sellerId 판매자 식별자
     * @param catalogSetNumber 카탈로그 세트 번호(nullable)
     * @param price 선점 시점의 확정 가격(원 단위)
     */
    record ReservedListing(
            String listingId, String sellerId, String catalogSetNumber, long price) {
    }
}
