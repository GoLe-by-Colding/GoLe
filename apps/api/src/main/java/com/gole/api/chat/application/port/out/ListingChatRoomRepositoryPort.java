package com.gole.api.chat.application.port.out;

import com.gole.api.chat.domain.model.ChatRoom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port: 매물 채팅방 저장소. 직거래 확인·완료는 조건부 갱신(CAS)으로만 기록해 동시 요청에도 한 번만 적용된다.
 */
public interface ListingChatRoomRepositoryPort {

    Optional<ChatRoom> findById(String roomId);

    Optional<ChatRoom> findByParticipants(String buyerId, String sellerId, String listingId);

    /** 참여 중인 방을 마지막 활동이 최근인 순으로 최대 {@code limit} 개. */
    List<ChatRoom> findRecentByParticipant(String accountId, int limit);

    /**
     * 새 방을 저장한다. 같은 (구매자, 판매자, 매물) 방이 동시에 만들어지면 먼저 저장된 방을 돌려준다(유니크 인덱스로 판정).
     */
    ChatRoom createOrGetExisting(ChatRoom room);

    /** 그 쪽이 아직 확인하지 않았고 완료 전일 때만 확인 시각을 기록한다. 이번 호출이 기록했으면 {@code true}. */
    boolean recordConfirmation(String roomId, ChatRoom.Party party, Instant confirmedAt);

    /** 양쪽 확인이 끝났고 완료 전일 때만 완료 시각을 기록한다. 이번 호출이 완료시켰으면 완료된 방. */
    Optional<ChatRoom> completeIfBothConfirmed(String roomId, Instant completedAt);

    /** 완료 전일 때만 그 쪽의 확인을 지운다. */
    void clearConfirmation(String roomId, ChatRoom.Party party);
}
