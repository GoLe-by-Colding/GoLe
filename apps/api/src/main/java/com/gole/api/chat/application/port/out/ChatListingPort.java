package com.gole.api.chat.application.port.out;

/** Outbound port: 매물 조회. 매물이 없거나 공개되지 않으면 매물 컨텍스트의 404 예외를 그대로 던진다. */
public interface ChatListingPort {

    /** 매물의 판매자(공개 여부와 무관 — 숨겨진 매물의 기존 방도 다시 열기 위해). */
    String sellerOf(String listingId);

    /** 새 방을 만들 수 있게 공개 중인 매물인지 확인한다. */
    void requirePublic(String listingId);
}
