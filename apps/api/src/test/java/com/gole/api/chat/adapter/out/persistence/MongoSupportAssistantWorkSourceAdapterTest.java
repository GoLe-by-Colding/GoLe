package com.gole.api.chat.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gole.api.chat.application.port.out.SupportAssistantPort.Request;
import com.gole.api.chat.domain.model.ChatRoomType;
import com.gole.api.chat.domain.model.SupportCategory;
import com.gole.api.chat.domain.model.SupportStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class MongoSupportAssistantWorkSourceAdapterTest {

    private static final Instant NOW = Instant.parse("2026-09-04T01:02:03Z");

    private final SupportTicketMongoRepository tickets = mock(SupportTicketMongoRepository.class);
    private final SocialChatRoomMongoRepository rooms = mock(SocialChatRoomMongoRepository.class);
    private final ChatMessageMongoRepository messages = mock(ChatMessageMongoRepository.class);
    private final MongoSupportAssistantWorkSourceAdapter source =
            new MongoSupportAssistantWorkSourceAdapter(tickets, rooms, messages);

    @Test
    void rebuildsAnalysisRequestFromCanonicalTicketRoomAndFirstRequesterMessage() {
        when(tickets.findById("room-1")).thenReturn(Optional.of(ticket("room-1", "requester-1")));
        when(rooms.findById("room-1")).thenReturn(Optional.of(room("room-1", "배송 문의")));
        when(messages.findFirstByRoomIdAndSenderIdOrderBySentAtAscIdAsc("room-1", "requester-1"))
                .thenReturn(
                        Optional.of(new ChatMessageDocument("message-1", "room-1", "requester-1", "언제 발송되나요?", NOW)));

        assertThat(source.findRequest("room-1"))
                .contains(new Request("room-1", SupportCategory.PRODUCT_FEEDBACK, "배송 문의", "언제 발송되나요?", "ko-KR"));
    }

    @Test
    void refusesNonSupportRoomOrMissingOpeningMessage() {
        SocialChatRoomDocument direct = new SocialChatRoomDocument(
                "room-1", "DIRECT", List.of("requester-1", "peer-1"), null, null, "key", NOW, NOW, null, 0);
        when(tickets.findById("room-1")).thenReturn(Optional.of(ticket("room-1", "requester-1")));
        when(rooms.findById("room-1")).thenReturn(Optional.of(direct));

        assertThat(source.findRequest("room-1")).isEmpty();
    }

    @Test
    void recentDiscoveryReturnsOnlyStableRoomIds() {
        when(tickets.findByStatusNot(eq(SupportStatus.RESOLVED.name()), any(Pageable.class)))
                .thenReturn(List.of(ticket("room-2", "requester-2"), ticket("room-1", "requester-1")));

        assertThat(source.findRecentRoomIds(200)).containsExactly("room-2", "room-1");
    }

    private static SupportTicketDocument ticket(String roomId, String requesterId) {
        return new SupportTicketDocument(
                roomId, requesterId, SupportCategory.PRODUCT_FEEDBACK.name(), "OPEN", null, NOW, NOW, null, 0);
    }

    private static SocialChatRoomDocument room(String roomId, String title) {
        return new SocialChatRoomDocument(
                roomId,
                ChatRoomType.SUPPORT.name(),
                List.of("requester-1"),
                "requester-1",
                title,
                null,
                NOW,
                NOW,
                null,
                0);
    }
}
