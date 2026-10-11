package com.gole.api.offer.application.port.out;

import java.util.Optional;

/** Outbound port: 채팅 컨텍스트. 매물 방 접근 검사와 방 메시지 남기기. */
public interface OfferChatPort {

    /**
     * 채팅 메시지 전송과 같은 검사(참여·종료·차단·정지)를 통과해야 한다. 통과한 방이 매물 방이 아니면 비어 있다.
     */
    Optional<ListingRoom> requireSendableListingRoom(String roomId, String actorId);

    /** 방 참여자만 통과한다. 통과한 방이 매물 방이 아니면 비어 있다. */
    Optional<ListingRoom> requireReadableListingRoom(String roomId, String actorId);

    /** {@code actorId} 명의로 방에 메시지를 남긴다. 실패는 호출한 쪽이 흡수한다. */
    void post(String roomId, String actorId, String content);

    record ListingRoom(String roomId, String listingId, String buyerId, String sellerId) {}
}
