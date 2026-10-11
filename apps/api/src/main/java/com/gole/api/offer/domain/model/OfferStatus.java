package com.gole.api.offer.domain.model;

import java.util.Locale;

/**
 * 가격 제안 상태.
 *
 * <p>{@code EXPIRED}는 대부분 저장되지 않는다. 만료는 스케줄러 없이 읽을 때 계산하는 "유효 상태"이고
 * (price-offer O12), 저장되는 경우는 새 제안이 자리를 비우려고 만료된 {@code PENDING}을 원자 전이할
 * 때뿐이다.
 */
public enum OfferStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    WITHDRAWN,
    EXPIRED;

    /** 아직 응답·철회가 가능한 상태인가. 거절과 철회는 이 상태에서만 할 수 있다. */
    public boolean isOpen() {
        return this == PENDING || this == ACCEPTED;
    }

    /** API 표기(소문자). */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
