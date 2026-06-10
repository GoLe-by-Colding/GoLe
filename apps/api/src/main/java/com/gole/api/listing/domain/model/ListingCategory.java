package com.gole.api.listing.domain.model;

/**
 * 매물 카테고리. 공식 세트뿐 아니라 부품(브릭링크식)·미니피그·창작품(MOC) 거래를 지원한다.
 * 레거시/미지정 매물은 SET 으로 간주한다.
 */
public enum ListingCategory {
    SET,
    PARTS,
    MINIFIG,
    MOC;

    /** 외부 노출 키(소문자). 프론트 카테고리 키와 일치. */
    public String key() {
        return name().toLowerCase();
    }

    /** 키 → enum. null/미상은 SET 으로 간주. */
    public static ListingCategory fromKey(String key) {
        if (key == null) {
            return SET;
        }
        for (ListingCategory c : values()) {
            if (c.key().equalsIgnoreCase(key) || c.name().equalsIgnoreCase(key)) {
                return c;
            }
        }
        return SET;
    }
}
