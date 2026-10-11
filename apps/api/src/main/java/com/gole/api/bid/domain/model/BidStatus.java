package com.gole.api.bid.domain.model;

/**
 * 입찰 상태. (buy-bids D4, D7, D9)
 *
 * <p>{@link #EXPIRED}는 기본적으로 읽을 때 계산하는 유효 상태다. 저장 상태로는 같은 사용자가 같은
 * 세트·상태로 새 입찰을 걸기 직전에만 쓴다 — 만료된 {@code ACTIVE}가 "사용자·세트·상태당 하나"인 유일
 * 인덱스 자리를 계속 차지하지 않게 비우기 위해서다.
 */
public enum BidStatus {
    ACTIVE,
    CANCELED,
    FILLED,
    EXPIRED
}
