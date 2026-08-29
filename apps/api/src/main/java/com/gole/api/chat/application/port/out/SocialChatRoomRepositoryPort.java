package com.gole.api.chat.application.port.out;

import com.gole.api.chat.domain.model.SocialChatRoom;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port: 유형 있는 채팅방 저장소.
 *
 * <p>구현은 <b>두 컬렉션</b>을 함께 본다. 매물 방은 기존 {@code chat_rooms} 에 그대로 남아 있고,
 * 새 유형은 별도 컬렉션에 쌓인다. 그 사실을 응용 계층이 알 필요는 없으므로 여기서 하나로 덮는다.
 */
public interface SocialChatRoomRepositoryPort {

    Optional<SocialChatRoom> findById(String roomId);

    /** DIRECT 멱등 조회. 참여자 정렬 키로 찾는다. */
    Optional<SocialChatRoom> findByDedupeKey(String dedupeKey);

    /** 내가 멤버인 방(최근 활동순). 레거시 매물 방도 포함한다. */
    List<SocialChatRoom> findByMember(String accountId, int limit);

    /** 내가 멤버인 신규 DIRECT·GROUP·SUPPORT 방만 최근 활동순으로 조회한다. */
    List<SocialChatRoom> findSocialByMember(String accountId, int limit);

    /** 메시지 전송 시 방의 최근 활동 시각을 갱신한다. 레거시 매물 방도 지원한다. */
    void touchActivity(String roomId, java.time.Instant occurredAt);

    /**
     * 저장한다.
     *
     * @throws com.gole.api.common.exception.ConflictException 같은 {@code dedupeKey} 가 이미 있을 때.
     *     경쟁 상황에서 두 요청이 동시에 같은 DM 을 만들면 하나만 성공해야 한다.
     */
    SocialChatRoom save(SocialChatRoom room);
}
