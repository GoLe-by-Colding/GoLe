package com.gole.api.chat.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.gole.api.account.adapter.in.web.UserAuthInterceptor;
import com.gole.api.account.application.service.SellerIdentityVerificationService;
import com.gole.api.account.application.service.ThirdPartyProvisionConsentService;
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
import com.gole.api.common.exception.ServiceUnavailableException;
import com.gole.api.common.operations.OperationalEventPublisher;
import com.gole.api.common.web.GlobalExceptionHandler;
import com.gole.api.listing.application.port.in.GetListingUseCase;
import com.gole.api.listing.domain.exception.ListingNotFoundException;
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
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

class ChatControllerTest {

    private final ChatRoomMongoRepository rooms = mock(ChatRoomMongoRepository.class);
    private final GetListingUseCase listings = mock(GetListingUseCase.class);
    private final RedisMessageListenerContainer listeners = mock(RedisMessageListenerContainer.class);
    private final SocialChatService socialChats = mock(SocialChatService.class);
    private final ChatMessagingService messaging = mock(ChatMessagingService.class);
    private final ChatReadService reads = mock(ChatReadService.class);
    private final DirectTradeService directTrades = mock(DirectTradeService.class);
    private final SupportTicketRepositoryPort supportTickets = mock(SupportTicketRepositoryPort.class);
    private final ThirdPartyProvisionConsentService thirdPartyProvisionConsents =
            mock(ThirdPartyProvisionConsentService.class);
    private final SellerIdentityVerificationService sellerIdentityVerification =
            mock(SellerIdentityVerificationService.class);
    private final ChatController controller = new ChatController(
            rooms,
            listeners,
            listings,
            new ObjectMapper(),
            directTrades,
            socialChats,
            messaging,
            reads,
            supportTickets,
            thirdPartyProvisionConsents,
            sellerIdentityVerification);

    @Test
    void createRoom_usesAuthenticatedBuyerAndListingSeller() {
        when(listings.getById("listing-1")).thenReturn(listing("real-seller"));
        when(listings.getPublicById("listing-1")).thenReturn(listing("real-seller"));
        when(rooms.findByBuyerIdAndSellerIdAndListingId("real-buyer", "real-seller", "listing-1"))
                .thenReturn(Optional.empty());
        when(rooms.save(any(ChatRoomDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MockHttpServletRequest request = authenticated("real-buyer");
        var response = controller.createOrGetRoom(
                new ChatController.CreateRoomRequest("listing-1", "forged-buyer", "forged-seller"), request);

        assertThat(response.buyerId()).isEqualTo("real-buyer");
        assertThat(response.sellerId()).isEqualTo("real-seller");
        verify(rooms).findByBuyerIdAndSellerIdAndListingId("real-buyer", "real-seller", "listing-1");
        verify(sellerIdentityVerification).requireVerifiedSeller("real-seller");
    }

    @Test
    void createRoom_requiresCurrentProvisionConsentBeforeCreatingRoom() {
        when(listings.getById("listing-1")).thenReturn(listing("seller"));
        when(rooms.findByBuyerIdAndSellerIdAndListingId("legacy-buyer", "seller", "listing-1"))
                .thenReturn(Optional.empty());
        doThrow(new ForbiddenException(ThirdPartyProvisionConsentService.REQUIRED_CODE, "consent required"))
                .when(thirdPartyProvisionConsents)
                .requireCurrent("legacy-buyer");

        assertThatThrownBy(() -> controller.createOrGetRoom(
                        new ChatController.CreateRoomRequest("listing-1", null, null), authenticated("legacy-buyer")))
                .isInstanceOf(ForbiddenException.class)
                .extracting("code")
                .isEqualTo(ThirdPartyProvisionConsentService.REQUIRED_CODE);

        verify(listings, never()).getPublicById("listing-1");
        verify(rooms, never()).save(any());
    }

    @Test
    void createRoom_requiresVerifiedListingSellerBeforeCreatingANewRoom() {
        when(listings.getById("listing-1")).thenReturn(listing("unverified-seller"));
        when(rooms.findByBuyerIdAndSellerIdAndListingId("buyer", "unverified-seller", "listing-1"))
                .thenReturn(Optional.empty());
        doThrow(new ServiceUnavailableException(
                        "SELLER_IDENTITY_VERIFICATION_UNAVAILABLE", "seller verification unavailable"))
                .when(sellerIdentityVerification)
                .requireVerifiedSeller("unverified-seller");

        assertThatThrownBy(() -> controller.createOrGetRoom(
                        new ChatController.CreateRoomRequest("listing-1", null, null), authenticated("buyer")))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasFieldOrPropertyWithValue("code", "SELLER_IDENTITY_VERIFICATION_UNAVAILABLE");

        verifyNoInteractions(thirdPartyProvisionConsents);
        verify(rooms, never()).save(any());
    }

    @Test
    void createRoom_requiresSellersConsentBeforeProvidingTheirIdentityToANewRoom() {
        when(listings.getById("listing-1")).thenReturn(listing("seller"));
        when(rooms.findByBuyerIdAndSellerIdAndListingId("buyer", "seller", "listing-1"))
                .thenReturn(Optional.empty());
        doThrow(new ForbiddenException(ThirdPartyProvisionConsentService.SUBJECT_REQUIRED_CODE, "subject consent"))
                .when(thirdPartyProvisionConsents)
                .requireCurrentSubject("seller");

        assertThatThrownBy(() -> controller.createOrGetRoom(
                        new ChatController.CreateRoomRequest("listing-1", null, null), authenticated("buyer")))
                .isInstanceOf(ForbiddenException.class)
                .extracting("code")
                .isEqualTo(ThirdPartyProvisionConsentService.SUBJECT_REQUIRED_CODE);

        verify(listings, never()).getPublicById("listing-1");
        verify(rooms, never()).save(any());
    }

    @Test
    void createRoom_rejectsChattingOnOwnListing() {
        when(listings.getById("listing-1")).thenReturn(listing("same-user"));

        assertThatThrownBy(() -> controller.createOrGetRoom(
                        new ChatController.CreateRoomRequest("listing-1", null, null), authenticated("same-user")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void createRoom_doesNotCreateRoomForHiddenListing() {
        when(listings.getById("deleted-listing")).thenReturn(listing("real-seller"));
        when(rooms.findByBuyerIdAndSellerIdAndListingId("real-buyer", "real-seller", "deleted-listing"))
                .thenReturn(Optional.empty());
        when(listings.getPublicById("deleted-listing")).thenThrow(new ListingNotFoundException("deleted-listing"));

        assertThatThrownBy(() -> controller.createOrGetRoom(
                        new ChatController.CreateRoomRequest("deleted-listing", null, null),
                        authenticated("real-buyer")))
                .isInstanceOf(ListingNotFoundException.class);
        verify(rooms, never()).save(any());
    }

    @Test
    void hiddenListingNewRoomReturnsNotFoundAtHttpBoundary() throws Exception {
        when(listings.getById("deleted-listing")).thenReturn(listing("real-seller"));
        when(rooms.findByBuyerIdAndSellerIdAndListingId("real-buyer", "real-seller", "deleted-listing"))
                .thenReturn(Optional.empty());
        when(listings.getPublicById("deleted-listing")).thenThrow(new ListingNotFoundException("deleted-listing"));
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(mock(OperationalEventPublisher.class)))
                .build();

        mvc.perform(post("/api/v1/chat/rooms")
                        .requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "real-buyer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listingId\":\"deleted-listing\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_FOUND"));
        verify(rooms, never()).save(any());
    }

    @Test
    void createRoom_returnsExistingRoomAfterListingWasHidden() {
        ChatRoomDocument existing =
                new ChatRoomDocument("room-1", "deleted-listing", "real-buyer", "real-seller", Instant.now());
        when(listings.getById("deleted-listing")).thenReturn(listing("real-seller"));
        when(rooms.findByBuyerIdAndSellerIdAndListingId("real-buyer", "real-seller", "deleted-listing"))
                .thenReturn(Optional.of(existing));

        var response = controller.createOrGetRoom(
                new ChatController.CreateRoomRequest("deleted-listing", null, null), authenticated("real-buyer"));

        assertThat(response.id()).isEqualTo("room-1");
        verifyNoInteractions(thirdPartyProvisionConsents);
        verifyNoInteractions(sellerIdentityVerification);
        verify(listings, never()).getPublicById("deleted-listing");
        verify(rooms, never()).save(any());
    }

    @Test
    void createRoom_returnsConcurrentWinnerWhenUniqueIndexWinsRace() {
        ChatRoomDocument winner =
                new ChatRoomDocument("winner", "listing-1", "real-buyer", "real-seller", Instant.now());
        when(listings.getById("listing-1")).thenReturn(listing("real-seller"));
        when(listings.getPublicById("listing-1")).thenReturn(listing("real-seller"));
        when(rooms.findByBuyerIdAndSellerIdAndListingId("real-buyer", "real-seller", "listing-1"))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(rooms.save(any(ChatRoomDocument.class))).thenThrow(new DuplicateKeyException("duplicate"));

        var response = controller.createOrGetRoom(
                new ChatController.CreateRoomRequest("listing-1", null, null), authenticated("real-buyer"));

        assertThat(response.id()).isEqualTo("winner");
    }

    @Test
    void directTradeConfirmationRequiresTheListingSellersVerifiedIdentity() {
        Instant now = Instant.parse("2026-09-04T00:00:00Z");
        ChatRoomDocument listingRoom = new ChatRoomDocument("room-1", "listing-1", "buyer-1", "seller-1", now);
        when(socialChats.requireReadable("room-1", "buyer-1"))
                .thenReturn(SocialChatRoom.listing("room-1", "listing-1", "buyer-1", "seller-1", now));
        when(rooms.findById("room-1")).thenReturn(Optional.of(listingRoom));
        doThrow(new ServiceUnavailableException(
                        "SELLER_IDENTITY_VERIFICATION_UNAVAILABLE", "seller verification unavailable"))
                .when(sellerIdentityVerification)
                .requireVerifiedSeller("seller-1");

        assertThatThrownBy(() -> controller.confirmDirectTrade("room-1", authenticated("buyer-1")))
                .isInstanceOf(ServiceUnavailableException.class);

        verify(directTrades, never()).confirm(any(), any());
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
        verify(thirdPartyProvisionConsents, never()).requireCurrent("buyer");
    }

    @Test
    void sendingNewNonSupportMessageRequiresConsentButSupportMessageDoesNot() {
        SocialChatRoom listingRoom =
                SocialChatRoom.listing("listing-room", "listing-1", "buyer", "seller", Instant.now());
        when(socialChats.requireReadable("listing-room", "buyer")).thenReturn(listingRoom);
        doThrow(new ForbiddenException(ThirdPartyProvisionConsentService.REQUIRED_CODE, "consent required"))
                .when(thirdPartyProvisionConsents)
                .requireCurrent("buyer");

        assertThatThrownBy(() -> controller.sendMessage(
                        "listing-room",
                        new ChatController.SendMessageRequest(null, "new message"),
                        authenticated("buyer")))
                .isInstanceOf(ForbiddenException.class);
        verify(messaging, never()).send("listing-room", "buyer", "new message");

        SocialChatRoom supportRoom = SocialChatRoom.support("support-room", "buyer", "privacy request", Instant.now());
        ChatMessage sent = new ChatMessage("message-1", "support-room", "buyer", "help", Instant.now());
        when(socialChats.requireReadable("support-room", "buyer")).thenReturn(supportRoom);
        when(messaging.send("support-room", "buyer", "help")).thenReturn(sent);

        assertThat(controller
                        .sendMessage(
                                "support-room",
                                new ChatController.SendMessageRequest(null, "help"),
                                authenticated("buyer"))
                        .id())
                .isEqualTo("message-1");
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
