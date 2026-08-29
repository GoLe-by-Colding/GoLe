package com.gole.api.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.chat.adapter.out.persistence.ChatMessageDocument;
import com.gole.api.chat.adapter.out.persistence.ChatMessageMongoRepository;
import com.gole.api.chat.application.port.out.ChatReadStatePort;
import com.gole.api.chat.domain.model.SocialChatRoom;
import com.gole.api.common.exception.BadRequestException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ChatReadServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-30T00:00:00Z");

    private final ChatMessageMongoRepository messages = mock(ChatMessageMongoRepository.class);
    private final ChatReadStatePort readStates = mock(ChatReadStatePort.class);
    private final SocialChatService socialChats = mock(SocialChatService.class);
    private final ChatReadService service =
            new ChatReadService(messages, readStates, socialChats, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void unreadCounts_rechecksEveryRoomAndExcludesStaleSupportAssignee() {
        SocialChatRoom direct = SocialChatRoom.direct("direct", "me", "peer", NOW);
        when(socialChats.myReadableRooms("me", 100)).thenReturn(List.of(direct));
        when(readStates.countUnread("me", List.of("direct"))).thenReturn(Map.of("direct", 2L));

        assertThat(service.unreadCounts("me")).containsExactly(Map.entry("direct", 2L));
        verify(socialChats).myReadableRooms("me", 100);
    }

    @Test
    void unreadCounts_returnsExplicitZeroForReadableRoom() {
        SocialChatRoom direct = SocialChatRoom.direct("direct", "me", "peer", NOW);
        when(socialChats.myReadableRooms("me", 100)).thenReturn(List.of(direct));
        when(readStates.countUnread("me", List.of("direct"))).thenReturn(Map.of());

        assertThat(service.unreadCounts("me")).containsExactly(Map.entry("direct", 0L));
    }

    @Test
    void unreadCountsIncludesEveryRoomFromSeparateSocialAndListingWindows() {
        List<SocialChatRoom> visibleRooms = IntStream.range(0, 200)
                .mapToObj(index -> SocialChatRoom.direct("room-" + index, "me", "peer", NOW))
                .toList();
        List<String> visibleIds = visibleRooms.stream().map(SocialChatRoom::id).toList();
        when(socialChats.myReadableRooms("me", 100)).thenReturn(visibleRooms);
        when(readStates.countUnread("me", visibleIds)).thenReturn(Map.of("room-199", 4L));

        assertThat(service.unreadCounts("me"))
                .hasSize(200)
                .containsEntry("room-0", 0L)
                .containsEntry("room-199", 4L);
    }

    @Test
    void markRead_checksMembershipBeforeResolvingMessageAndAdvancesExactPosition() {
        SocialChatRoom direct = SocialChatRoom.direct("direct", "me", "peer", NOW);
        Instant sentAt = NOW.minusSeconds(10);
        when(socialChats.requireReadable("direct", "me")).thenReturn(direct);
        when(messages.findById("message-1"))
                .thenReturn(Optional.of(new ChatMessageDocument("message-1", "direct", "peer", "hello", sentAt)));

        service.markRead("direct", "me", "message-1");

        verify(socialChats).requireReadable("direct", "me");
        verify(readStates).advance("direct", "me", "message-1", sentAt, NOW);
    }

    @Test
    void markRead_rejectsMessageFromAnotherRoom() {
        when(messages.findById("foreign"))
                .thenReturn(Optional.of(new ChatMessageDocument("foreign", "another", "peer", "secret", NOW)));

        assertThatThrownBy(() -> service.markRead("direct", "me", "foreign"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("읽음 위치");
        verify(socialChats).requireReadable("direct", "me");
    }
}
