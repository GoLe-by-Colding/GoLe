package com.gole.api.chat.application.port.in;

import java.util.Optional;

/**
 * Inbound port: 매물 채팅방 접근 검사와 거래 당사자 확인. 다른 컨텍스트(가격 제안)가 방 권한 규칙을
 * 다시 구현하지 않고 채팅의 규칙을 그대로 쓰게 한다.
 *
 * <p>검사를 통과하지 못하면 채팅 컨텍스트의 예외(방 없음·참여자 아님·종료·차단·정지)를 그대로 던진다.
 * 통과했지만 매물 방이 아니면 비어 있다 — 그 경우의 오류 코드는 호출한 쪽이 정한다.
 */
public interface ListingRoomAccessUseCase {

    /** 메시지 전송과 같은 검사({@code requireSendable}). */
    Optional<ListingRoomParticipants> requireSendableListingRoom(String roomId, String actorId);

    /** 방 읽기 검사({@code requireReadable}). */
    Optional<ListingRoomParticipants> requireReadableListingRoom(String roomId, String actorId);

    record ListingRoomParticipants(String roomId, String listingId, String buyerId, String sellerId) {}
}
