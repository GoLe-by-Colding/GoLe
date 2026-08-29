package com.gole.api.chat.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.account.adapter.in.web.UserAuthInterceptor;
import com.gole.api.chat.adapter.out.persistence.ChatRoomDocument;
import com.gole.api.chat.adapter.out.persistence.ChatRoomMongoRepository;
import com.gole.api.chat.application.ChatMessagingService;
import com.gole.api.chat.application.ChatReadService;
import com.gole.api.chat.application.DirectTradeService;
import com.gole.api.chat.application.SocialChatService;
import com.gole.api.chat.application.port.out.SupportTicketRepositoryPort;
import com.gole.api.chat.domain.model.ChatMessage;
import com.gole.api.chat.domain.model.SocialChatRoom;
import com.gole.api.chat.domain.model.SupportStatus;
import com.gole.api.chat.domain.model.SupportTicket;
import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.listing.application.port.in.GetListingUseCase;
import com.gole.api.listing.domain.model.ConditionDisclosure;
import com.gole.api.listing.domain.model.ItemCondition;
import com.gole.api.listing.domain.model.Listing;
import com.gole.api.listing.domain.model.ListingCategory;
import com.gole.api.listing.domain.model.ListingStatus;
import com.gole.api.listing.domain.model.Money;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.ObjectMapper;

class ChatControllerTest {

    private final ChatRoomMongoRepository rooms = mock(ChatRoomMongoRepository.class);
    private final GetListingUseCase listings = mock(GetListingUseCase.class);
    private final RedisMessageListenerContainer listeners = mock(RedisMessageListenerContainer.class);
    private final SocialChatService socialChats = mock(SocialChatService.class);
    private final ChatMessagingService messaging = mock(ChatMessagingService.class);
    private final ChatReadService reads = mock(ChatReadService.class);
    private final SupportTicketRepositoryPort supportTickets = mock(SupportTicketRepositoryPort.class);
    private final ChatController controller = new ChatController(
            rooms,
            listeners,
            listings,
            new ObjectMapper(),
            mock(DirectTradeService.class),
            socialChats,
            messaging,
            reads,
            supportTickets);

    @Test
    void createRoom_usesAuthenticatedBuyerAndListingSeller() {
        when(listings.getById("listing-1")).thenReturn(listing("real-seller"));
        when(rooms.findByBuyerIdAndSellerIdAndListingId("real-buyer", "real-seller", "listing-1"))
                .thenReturn(Optional.empty());
        when(rooms.save(any(ChatRoomDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MockHttpServletRequest request = authenticated("real-buyer");
        var response = controller.createOrGetRoom(
                new ChatController.CreateRoomRequest("listing-1", "forged-buyer", "forged-seller"), request);

        assertThat(response.buyerId()).isEqualTo("real-buyer");
        assertThat(response.sellerId()).isEqualTo("real-seller");
        verify(rooms).findByBuyerIdAndSellerIdAndListingId("real-buyer", "real-seller", "listing-1");
    }

    @Test
    void createRoom_rejectsChattingOnOwnListing() {
        when(listings.getById("listing-1")).thenReturn(listing("same-user"));

        assertThatThrownBy(() -> controller.createOrGetRoom(
                        new ChatController.CreateRoomRequest("listing-1", null, null), authenticated("same-user")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void createRoom_returnsConcurrentWinnerWhenUniqueIndexWinsRace() {
        ChatRoomDocument winner =
                new ChatRoomDocument("winner", "listing-1", "real-buyer", "real-seller", Instant.now());
        when(listings.getById("listing-1")).thenReturn(listing("real-seller"));
        when(rooms.findByBuyerIdAndSellerIdAndListingId("real-buyer", "real-seller", "listing-1"))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(rooms.save(any(ChatRoomDocument.class))).thenThrow(new DuplicateKeyException("duplicate"));

        var response = controller.createOrGetRoom(
                new ChatController.CreateRoomRequest("listing-1", null, null), authenticated("real-buyer"));

        assertThat(response.id()).isEqualTo("winner");
    }

    @Test
    void myRooms_usesAuthenticatedUserAndAppliesRepositoryLimit() {
        when(rooms.findTop100ByBuyerIdOrSellerIdOrderByLastMessageAtDesc("account-1", "account-1"))
                .thenReturn(List.of());

        assertThat(controller.myRooms(authenticated("account-1"))).isEmpty();

        verify(rooms).findTop100ByBuyerIdOrSellerIdOrderByLastMessageAtDesc("account-1", "account-1");
    }

    @Test
    void roomResolvesListingOutsideTheRecentRoomWindowAfterPermissionCheck() {
        Instant now = Instant.parse("2026-08-30T00:00:00Z");
        ChatRoomDocument listing = new ChatRoomDocument("room-old", "listing-1", "account-1", "seller-1", now);
        when(socialChats.requireReadable("room-old", "account-1"))
                .thenReturn(SocialChatRoom.listing("room-old", "listing-1", "account-1", "seller-1", now));
        when(rooms.findById("room-old")).thenReturn(Optional.of(listing));

        var response = controller.room("room-old", authenticated("account-1"));

        assertThat(response.kind()).isEqualTo("LISTING");
        assertThat(response.listingRoom().id()).isEqualTo("room-old");
        assertThat(response.socialRoom()).isNull();
        verify(socialChats).requireReadable("room-old", "account-1");
    }

    @Test
    void roomResolvesSupportUsingCurrentTicketState() {
        Instant now = Instant.parse("2026-08-30T00:00:00Z");
        SocialChatRoom support = SocialChatRoom.support("support-old", "account-1", "정산 문의", now);
        SupportTicket ticket =
                new SupportTicket("support-old", "account-1", SupportStatus.IN_PROGRESS, "admin-1", now, now, null);
        when(socialChats.requireReadable("support-old", "account-1")).thenReturn(support);
        when(supportTickets.findByRoomId("support-old")).thenReturn(Optional.of(ticket));

        var response = controller.room("support-old", authenticated("account-1"));

        assertThat(response.kind()).isEqualTo("SOCIAL");
        assertThat(response.socialRoom().supportStatus()).isEqualTo("IN_PROGRESS");
        assertThat(response.listingRoom()).isNull();
    }

    @Test
    void unreadCounts_andMarkRead_useOnlyAuthenticatedAccount() {
        when(reads.unreadCounts("account-1")).thenReturn(java.util.Map.of("room-1", 3L));

        assertThat(controller.unreadCounts(authenticated("account-1"))).containsEntry("room-1", 3L);
        controller.markRead("room-1", new ChatController.MarkReadRequest("message-1"), authenticated("account-1"));

        verify(reads).unreadCounts("account-1");
        verify(reads).markRead("room-1", "account-1", "message-1");
    }

    @Test
    void messages_readsOnlyRecentBatchAndReturnsChronologicalOrder() {
        ChatMessage older =
                new ChatMessage("message-1", "room-1", "buyer", "old", Instant.parse("2026-08-09T00:01:00Z"));
        ChatMessage newest =
                new ChatMessage("message-2", "room-1", "seller", "new", Instant.parse("2026-08-09T00:02:00Z"));
        when(messaging.history("room-1", "buyer", null, null, 60)).thenReturn(List.of(older, newest));

        var response = controller.messages("room-1", null, null, 60, authenticated("buyer"));

        assertThat(response).extracting(ChatController.MessageResponse::id).containsExactly("message-1", "message-2");
        verify(messaging).history("room-1", "buyer", null, null, 60);
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

        listener.getValue().onMessage(invalid, null);

        verify(listeners).removeMessageListener(listener.getValue(), topic.getValue());
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
        request.setAttribute(UserAuthInterceptor.ATTR_ACCOUNT_ID, accountId);
        return request;
    }

    private static Listing listing(String sellerId) {
        return new Listing(
                "listing-1",
                sellerId,
                "레고 세트",
                "설명",
                Money.won(10_000),
                ItemCondition.USED_GOOD,
                ConditionDisclosure.basic(),
                List.of("photo.jpg"),
                "10307",
                ListingCategory.SET,
                ListingStatus.ACTIVE,
                Instant.parse("2026-08-09T00:00:00Z"));
    }
}
