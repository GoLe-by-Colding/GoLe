package com.gole.api.offer.domain.model;

import java.util.Locale;

/** 제안이 어디서 왔는가. 채팅 제안은 방이 있고, 입찰 체결로 생긴 제안은 방 없이 바로 수락 상태다. */
public enum OfferOrigin {
    CHAT,
    BID;

    /** API 표기(소문자). */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
