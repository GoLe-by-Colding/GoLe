package com.gole.api.chat.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.account.adapter.in.web.UserAuthInterceptor;
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
    private final ChatController controller = new ChatController(
            rooms,
            mock(ChatMessageMongoRepository.class),
            mock(ChatRedisPublisher.class),
            listeners,
            listings,
            new ObjectMapper());

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
