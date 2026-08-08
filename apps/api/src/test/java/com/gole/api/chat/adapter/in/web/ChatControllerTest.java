package com.gole.api.chat.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.account.adapter.in.web.UserAuthInterceptor;
import com.gole.api.chat.adapter.out.persistence.ChatMessageDocument;
import com.gole.api.chat.adapter.out.persistence.ChatMessageMongoRepository;
import com.gole.api.chat.adapter.out.persistence.ChatRoomDocument;
import com.gole.api.chat.adapter.out.persistence.ChatRoomMongoRepository;
import com.gole.api.chat.adapter.out.pubsub.ChatRedisPublisher;
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
    private final ChatMessageMongoRepository messages = mock(ChatMessageMongoRepository.class);
    private final GetListingUseCase listings = mock(GetListingUseCase.class);
    private final RedisMessageListenerContainer listeners = mock(RedisMessageListenerContainer.class);
    private final ChatController controller = new ChatController(
            rooms, messages, mock(ChatRedisPublisher.class), listeners, listings, new ObjectMapper());

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
        when(rooms.findTop100ByBuyerIdOrSellerIdOrderByCreatedAtDesc("account-1", "account-1"))
                .thenReturn(List.of());

        assertThat(controller.myRooms(authenticated("account-1"))).isEmpty();

        verify(rooms).findTop100ByBuyerIdOrSellerIdOrderByCreatedAtDesc("account-1", "account-1");
    }

    @Test
    void messages_readsOnlyRecentBatchAndReturnsChronologicalOrder() {
        when(rooms.findById("room-1"))
                .thenReturn(Optional.of(new ChatRoomDocument(
                        "room-1", "listing-1", "buyer", "seller", Instant.parse("2026-08-09T00:00:00Z"))));
        ChatMessageDocument newest =
                new ChatMessageDocument("message-2", "room-1", "seller", "new", Instant.parse("2026-08-09T00:02:00Z"));
        ChatMessageDocument older =
                new ChatMessageDocument("message-1", "room-1", "buyer", "old", Instant.parse("2026-08-09T00:01:00Z"));
        when(messages.findTop60ByRoomIdOrderBySentAtDesc("room-1")).thenReturn(List.of(newest, older));

        var response = controller.messages("room-1", authenticated("buyer"));

        assertThat(response).extracting(ChatController.MessageResponse::id).containsExactly("message-1", "message-2");
        verify(messages).findTop60ByRoomIdOrderBySentAtDesc("room-1");
    }

    @Test
    void stream_removesRedisListenerWhenPayloadHandlingFails() {
        when(rooms.findById("room-1"))
                .thenReturn(Optional.of(new ChatRoomDocument("room-1", "listing-1", "buyer", "seller", Instant.now())));
        controller.stream("room-1", authenticated("buyer"));

        ArgumentCaptor<MessageListener> listener = ArgumentCaptor.forClass(MessageListener.class);
        ArgumentCaptor<ChannelTopic> topic = ArgumentCaptor.forClass(ChannelTopic.class);
        verify(listeners).addMessageListener(listener.capture(), topic.capture());
        Message invalid = mock(Message.class);
        when(invalid.getBody()).thenReturn("not-json".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        listener.getValue().onMessage(invalid, null);

        verify(listeners).removeMessageListener(listener.getValue(), topic.getValue());
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
                ItemCondition.USED_COMPLETE,
                ConditionDisclosure.basic(),
                List.of("photo.jpg"),
                "10307",
                ListingCategory.SET,
                ListingStatus.ACTIVE,
                Instant.parse("2026-08-09T00:00:00Z"));
    }
}
