package com.gole.api.listing.domain.model;

/**
 * 리스팅 상태.
 * ACTIVE: 노출/거래 가능, RESERVED: 진행 중 주문으로 예약됨,
 * SOLD: 판매 완료, DELETED: 삭제됨.
 */
public enum ListingStatus {
    ACTIVE,
    RESERVED,
    SOLD,
    DELETED
}
