package com.gole.api.chat.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.gole.api.chat.application.port.in.ChatMessagingUseCase;
import com.gole.api.chat.application.port.in.ChatReadStateUseCase;
import com.gole.api.chat.application.port.in.DirectTradeUseCase;
import com.gole.api.chat.application.port.in.ListingChatRoomUseCase;
import com.gole.api.chat.application.port.in.ListingChatRoomUseCase.ResolvedChatRoom;
import com.gole.api.chat.application.port.in.SocialChatUseCase;
import com.gole.api.chat.domain.model.ChatMessage;
import com.gole.api.chat.domain.model.ChatRoom;
import com.gole.api.chat.domain.model.SocialChatRoom;
import com.gole.api.chat.domain.model.SupportStatus;
import com.gole.api.chat.domain.model.SupportTicket;
import com.gole.api.common.operations.OperationalEventPublisher;
import com.gole.api.common.web.GlobalExceptionHandler;
import com.gole.api.common.web.auth.AuthenticatedUser;
import com.gole.api.listing.domain.exception.ListingNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

/** 채팅 REST·SSE 경계. 방 개설·직거래·동의 규칙은 서비스 테스트(ListingChatRoomServiceTest 등)가 본다. */
class ChatControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-30T00:00:00Z");

    private final ListingChatRoomUseCase rooms = mock(ListingChatRoomUseCase.class);
    private final SocialChatUseCase socialChats = mock(SocialChatUseCase.class);
    private final ChatMessagingUseCase messaging = mock(ChatMessagingUseCase.class);
    private final ChatReadStateUseCase reads = mock(ChatReadStateUseCase.class);
    private final DirectTradeUseCase directTrades = mock(DirectTradeUseCase.class);
    private final RedisMessageListenerContainer listeners = mock(RedisMessageListenerContainer.class);
    private final ChatController controller =
            new ChatController(rooms, socialChats, messaging, reads, directTrades, listeners, new ObjectMapper());

    @Test
    @DisplayName("방 개설은 세션 계정을 구매자로 쓰고 본문의 구매자·판매자 값은 무시한다")
    void createRoom_usesAuthenticatedBuyer() {
        when(rooms.open("real-buyer", "listing-1"))
                .thenReturn(ChatRoom.open("room-1", "listing-1", "real-buyer", "real-seller", NOW));

        var response = controller.createOrGetRoom(
                new ChatController.CreateRoomRequest("listing-1", "forged-buyer", "forged-seller"),
                authenticated("real-buyer"));

        assertThat(response.buyerId()).isEqualTo("real-buyer");
        assertThat(response.sellerId()).isEqualTo("real-seller");
        verify(rooms).open("real-buyer", "listing-1");
    }

    @Test
    @DisplayName("숨긴 매물에 새 방을 열면 HTTP 경계에서 404 다")
    void hiddenListingNewRoomReturnsNotFoundAtHttpBoundary() throws Exception {
        when(rooms.open("real-buyer", "deleted-listing")).thenThrow(new ListingNotFoundException("deleted-listing"));
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(mock(OperationalEventPublisher.class)))
                .build();

        mvc.perform(post("/api/v1/chat/rooms")
                        .requestAttr(AuthenticatedUser.ATTRIBUTE, "real-buyer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listingId\":\"deleted-listing\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_FOUND"));
    }

    @Test
    @DisplayName("방 단건은 매물 방이면 LISTING, 아니면 SOCIAL 로 내린다")
    void room_mapsResolvedKind() {
        ChatRoom listingRoom = ChatRoom.open("room-old", "listing-1", "account-1", "seller-1", NOW);
        when(rooms.resolve("room-old", "account-1")).thenReturn(new ResolvedChatRoom(listingRoom, null, null));
        SocialChatRoom support = SocialChatRoom.support("support-old", "account-1", "정산 문의", NOW);
        SupportTicket ticket =
                new SupportTicket("support-old", "account-1", SupportStatus.IN_PROGRESS, "admin-1", NOW, NOW, null);
        when(rooms.resolve("support-old", "account-1")).thenReturn(new ResolvedChatRoom(null, support, ticket));

        var listing = controller.room("room-old", authenticated("account-1"));
        var social = controller.room("support-old", authenticated("account-1"));

        assertThat(listing.kind()).isEqualTo("LISTING");
        assertThat(listing.listingRoom().id()).isEqualTo("room-old");
        assertThat(social.kind()).isEqualTo("SOCIAL");
        assertThat(social.socialRoom().supportStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("안 읽음 수·읽음 처리는 세션 계정으로만 한다")
    void unreadCounts_andMarkRead_useOnlyAuthenticatedAccount() {
        when(reads.unreadCounts("account-1")).thenReturn(Map.of("room-1", 3L));

        assertThat(controller.unreadCounts(authenticated("account-1"))).containsEntry("room-1", 3L);
        controller.markRead("room-1", new ChatController.MarkReadRequest("message-1"), authenticated("account-1"));

        verify(reads).markRead("room-1", "account-1", "message-1");
    }

    @Test
    @DisplayName("메시지 이력은 유스케이스가 준 순서(오래된 순) 그대로 내린다")
    void messages_returnsChronologicalOrder() {
        ChatMessage older = new ChatMessage("message-1", "room-1", "buyer", "old", NOW);
        ChatMessage newest = new ChatMessage("message-2", "room-1", "seller", "new", NOW.plusSeconds(60));
        when(messaging.history("room-1", "buyer", null, null, 60)).thenReturn(List.of(older, newest));

        var response = controller.messages("room-1", null, null, 60, authenticated("buyer"));

        assertThat(response).extracting(ChatController.MessageResponse::id).containsExactly("message-1", "message-2");
    }

    @Test
    @DisplayName("메시지 전송·직거래 확인은 세션 계정으로 유스케이스에 넘긴다")
    void sendAndConfirm_delegateWithAuthenticatedAccount() {
        when(messaging.sendFromUser("room-1", "buyer", "hello"))
                .thenReturn(new ChatMessage("message-1", "room-1", "buyer", "hello", NOW));
        when(directTrades.confirm("room-1", "buyer"))
                .thenReturn(ChatRoom.open("room-1", "listing-1", "buyer", "seller", NOW));

        assertThat(controller
                        .sendMessage(
                                "room-1",
                                new ChatController.SendMessageRequest("forged", "hello"),
                                authenticated("buyer"))
                        .senderId())
                .isEqualTo("buyer");
        assertThat(controller
                        .confirmDirectTrade("room-1", authenticated("buyer"))
                        .id())
                .isEqualTo("room-1");
    }

    @Test
    void stream_removesRedisListenerWhenPayloadHandlingFails() {
        when(socialChats.requireReadable("room-1", "buyer"))
                .thenReturn(SocialChatRoom.listing("room-1", "listing-1", "buyer", "seller", Instant.now()));
        when(messaging.after("room-1", "buyer", null, 200)).thenReturn(List.of());
        controller.stream("room-1", null, null, authenticated("buyer"));

        ArgumentCaptor<MessageListener> listener = ArgumentCaptor.forClass(MessageListener.class);
        ArgumentCaptor<ChannelTopic> topic = ArgumentCaptor.forClass(ChannelTopic.class);
        verify(listeners).addMessageListener(listener.capture(), topic.capture());
        Message invalid = mock(Message.class);
        when(invalid.getBody()).thenReturn("not-json".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        Logger logger = (Logger) LoggerFactory.getLogger(ChatController.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            listener.getValue().onMessage(invalid, null);
        } finally {
            logger.detachAppender(appender);
        }

        verify(listeners).removeMessageListener(listener.getValue(), topic.getValue());
        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage()).contains("errorType=");
            assertThat(event.getFormattedMessage()).doesNotContain("not-json");
        });
    }

    @Test
    void stream_prefersLastEventIdWhenReplayingMissedMessages() {
        when(socialChats.requireReadable("room-1", "buyer"))
                .thenReturn(SocialChatRoom.listing("room-1", "listing-1", "buyer", "seller", Instant.now()));
        when(messaging.after("room-1", "buyer", "last-delivered", 200)).thenReturn(List.of());

        var emitter = controller.stream("room-1", "initial-history", "last-delivered", authenticated("buyer"));

        verify(messaging).after("room-1", "buyer", "last-delivered", 200);
        emitter.complete();
    }

    @Test
    void stream_replaysEveryBatchAfterLastDeliveredMessage() {
        when(socialChats.requireReadable("room-1", "buyer"))
                .thenReturn(SocialChatRoom.listing("room-1", "listing-1", "buyer", "seller", Instant.now()));
        List<ChatMessage> firstBatch = IntStream.rangeClosed(1, 200)
                .mapToObj(index -> new ChatMessage(
                        "m" + index,
                        "room-1",
                        "buyer",
                        "message " + index,
                        Instant.parse("2026-08-09T00:00:00Z").plusSeconds(index)))
                .toList();
        ChatMessage finalMessage =
                new ChatMessage("m201", "room-1", "seller", "last", Instant.parse("2026-08-09T00:03:21Z"));
        when(messaging.after("room-1", "buyer", "m0", 200)).thenReturn(firstBatch);
        when(messaging.after("room-1", "buyer", "m200", 200)).thenReturn(List.of(finalMessage));

        var emitter = controller.stream("room-1", "m0", null, authenticated("buyer"));

        verify(messaging).after("room-1", "buyer", "m0", 200);
        verify(messaging).after("room-1", "buyer", "m200", 200);
        emitter.complete();
    }

    private static MockHttpServletRequest authenticated(String accountId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AuthenticatedUser.ATTRIBUTE, accountId);
        return request;
    }
}
