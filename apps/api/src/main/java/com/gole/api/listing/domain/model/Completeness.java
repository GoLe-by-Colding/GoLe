package com.gole.api.listing.domain.model;

/**
 * 구성(완비도). 박스·설명서·부품 포함 정도. (상품 상태 고지)
 */
public enum Completeness {
    /** 풀박스: 박스 + 설명서 + 부품 완비 */
    FULL_BOX,
    /** 박스 없음: 부품/설명서 위주(박스 없거나 손상) */
    NO_BOX,
    /** 벌크: 부품만(박스·설명서 없음) */
    BULK
}
