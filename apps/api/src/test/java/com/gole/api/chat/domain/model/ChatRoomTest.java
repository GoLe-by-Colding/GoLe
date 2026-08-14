package com.gole.api.chat.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * 채팅방 참여자 판정.
 *
 * <p>1:1 대화는 "누가 볼 수 있는가"가 곧 기능의 일부다. 이 규칙을 컨트롤러의 if 문으로
 * 흩어놓으면 엔드포인트가 늘어날 때마다 빠뜨린다(실제로 조회·전송·스트림 세 곳에서 필요하다).
 */
class ChatRoomTest {

    private static final ChatRoom ROOM =
            new ChatRoom("room-1", "listing-1", "buyer-1", "seller-1", Instant.parse("2026-08-14T00:00:00Z"));

    @Test
    void buyerAndSeller_areParticipants() {
        assertThat(ROOM.isParticipant("buyer-1")).isTrue();
        assertThat(ROOM.isParticipant("seller-1")).isTrue();
    }

    @Test
    void others_areNotParticipants() {
        assertThat(ROOM.isParticipant("stranger")).isFalse();
    }

    /** 인증 정보가 비어 있는 경로로 들어와도 통과되면 안 된다. */
    @Test
    void nullOrBlank_isNotParticipant() {
        assertThat(ROOM.isParticipant(null)).isFalse();
        assertThat(ROOM.isParticipant("")).isFalse();
        assertThat(ROOM.isParticipant("   ")).isFalse();
    }
}
