package com.gole.api.chat.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.chat.adapter.out.persistence.ChatRoomDocument;
import com.gole.api.chat.adapter.out.persistence.ChatRoomMongoRepository;
import com.gole.api.chat.application.port.in.ListingRoomAccessUseCase.ListingRoomParticipants;
import com.gole.api.chat.domain.model.ChatMessage;
import com.gole.api.chat.domain.model.SocialChatRoom;
import com.gole.api.common.exception.ForbiddenException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ListingRoomAccessServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");

    private final SocialChatService socialChats = mock(SocialChatService.class);
    private final ChatRoomMongoRepository listingRooms = mock(ChatRoomMongoRepository.class);
    private final ListingRoomAccessService service = new ListingRoomAccessService(socialChats, listingRooms);

    @Test
    @DisplayName("전송 검사를 통과한 매물 방이면 chat_rooms의 구매자·판매자를 돌려준다")
    void requireSendableListingRoom_returnsParticipantsFromListingRoomDocument() {
        when(socialChats.requireSendable("room-1", "buyer-1"))
                .thenReturn(SocialChatRoom.listing("room-1", "listing-1", "buyer-1", "seller-1", T0));
        when(listingRooms.findById("room-1"))
                .thenReturn(Optional.of(new ChatRoomDocument("room-1", "listing-1", "buyer-1", "seller-1", T0)));

        assertThat(service.requireSendableListingRoom("room-1", "buyer-1"))
                .contains(new ListingRoomParticipants("room-1", "listing-1", "buyer-1", "seller-1"));
    }

    @Test
    @DisplayName("매물 방이 아니면 비어 있고 채팅 오류는 그대로 낸다")
    void nonListingRoomIsEmpty_andChatGuardFailuresPropagate() {
        when(socialChats.requireReadable("room-dm", "buyer-1"))
                .thenReturn(SocialChatRoom.direct("room-dm", "buyer-1", "seller-1", T0));
        assertThat(service.requireReadableListingRoom("room-dm", "buyer-1")).isEmpty();
        verify(listingRooms, never()).findById("room-dm");

        when(socialChats.requireSendable("room-1", "stranger"))
                .thenThrow(new ForbiddenException("CHAT_NOT_A_MEMBER", "not a member"));
        assertThatThrownBy(() -> service.requireSendableListingRoom("room-1", "stranger"))
                .extracting("code")
                .isEqualTo("CHAT_NOT_A_MEMBER");
    }

    @Test
    @DisplayName("메시지 게시는 기존 사용자 전송 경로에 위임한다")
    void postDelegatesToUserSendPath() {
        ChatMessagingService messaging = mock(ChatMessagingService.class);
        ChatMessage sent = new ChatMessage("message-1", "room-1", "buyer-1", "[가격 제안] 1,000원을 제안했어요", T0);
        when(messaging.send("room-1", "buyer-1", "[가격 제안] 1,000원을 제안했어요")).thenReturn(sent);

        assertThat(new PostChatMessageService(messaging).post("room-1", "buyer-1", "[가격 제안] 1,000원을 제안했어요"))
                .isSameAs(sent);
    }
}
