package com.gole.api.chat.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gole.api.chat.application.port.out.ChatConsentPort;
import com.gole.api.chat.application.port.out.ChatListingPort;
import com.gole.api.chat.application.port.out.ChatSellerVerificationPort;
import com.gole.api.chat.application.port.out.ListingChatRoomRepositoryPort;
import com.gole.api.chat.domain.model.ChatRoom;
import com.gole.api.chat.domain.model.SocialChatRoom;
import com.gole.api.chat.domain.model.SupportStatus;
import com.gole.api.chat.domain.model.SupportTicket;
import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.common.exception.ServiceUnavailableException;
import com.gole.api.listing.domain.exception.ListingNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ListingChatRoomServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-30T00:00:00Z");

    private final ListingChatRoomRepositoryPort rooms = mock(ListingChatRoomRepositoryPort.class);
    private final SocialChatService socialChats = mock(SocialChatService.class);
    private final ChatListingPort listings = mock(ChatListingPort.class);
    private final ChatConsentPort consents = mock(ChatConsentPort.class);
    private final ChatSellerVerificationPort sellerVerification = mock(ChatSellerVerificationPort.class);
    private final ListingChatRoomService service = new ListingChatRoomService(
            rooms, socialChats, listings, consents, sellerVerification, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("구매자는 세션 계정, 판매자는 매물의 판매자로 새 방을 만든다")
    void open_usesBuyerAndListingSeller() {
        when(listings.sellerOf("listing-1")).thenReturn("real-seller");
        when(rooms.findByParticipants("real-buyer", "real-seller", "listing-1")).thenReturn(Optional.empty());
        when(rooms.createOrGetExisting(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ChatRoom room = service.open("real-buyer", "listing-1");

        assertThat(room.buyerId()).isEqualTo("real-buyer");
        assertThat(room.sellerId()).isEqualTo("real-seller");
        assertThat(room.createdAt()).isEqualTo(NOW);
        verify(socialChats).requireCanStartPrivateConversation("real-buyer", "real-seller");
        verify(sellerVerification).requireVerifiedSeller("real-seller");
        verify(listings).requirePublic("listing-1");
    }

    @Test
    @DisplayName("구매자의 현재 동의가 없으면 새 방을 만들지 않는다")
    void open_requiresBuyersConsentBeforeCreatingRoom() {
        when(listings.sellerOf("listing-1")).thenReturn("seller");
        when(rooms.findByParticipants("legacy-buyer", "seller", "listing-1")).thenReturn(Optional.empty());
        doThrow(new ForbiddenException("THIRD_PARTY_PROVISION_CONSENT_REQUIRED", "consent required"))
                .when(consents)
                .requireCurrent("legacy-buyer");

        assertThatThrownBy(() -> service.open("legacy-buyer", "listing-1"))
                .isInstanceOf(ForbiddenException.class)
                .extracting("code")
                .isEqualTo("THIRD_PARTY_PROVISION_CONSENT_REQUIRED");
        verify(listings, never()).requirePublic("listing-1");
        verify(rooms, never()).createOrGetExisting(any());
    }

    @Test
    @DisplayName("판매자 신원확인이 안 되면 동의를 묻기 전에 거부한다")
    void open_requiresVerifiedSellerBeforeAnythingElse() {
        when(listings.sellerOf("listing-1")).thenReturn("unverified-seller");
        when(rooms.findByParticipants("buyer", "unverified-seller", "listing-1"))
                .thenReturn(Optional.empty());
        doThrow(new ServiceUnavailableException("SELLER_IDENTITY_VERIFICATION_UNAVAILABLE", "unavailable"))
                .when(sellerVerification)
                .requireVerifiedSeller("unverified-seller");

        assertThatThrownBy(() -> service.open("buyer", "listing-1"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasFieldOrPropertyWithValue("code", "SELLER_IDENTITY_VERIFICATION_UNAVAILABLE");
        verifyNoInteractions(consents);
        verify(rooms, never()).createOrGetExisting(any());
    }

    @Test
    @DisplayName("판매자(정보주체)의 동의가 없으면 그 사람을 새 방에 내보이지 않는다")
    void open_requiresSellersConsent() {
        when(listings.sellerOf("listing-1")).thenReturn("seller");
        when(rooms.findByParticipants("buyer", "seller", "listing-1")).thenReturn(Optional.empty());
        doThrow(new ForbiddenException("THIRD_PARTY_PROVISION_SUBJECT_CONSENT_REQUIRED", "subject consent"))
                .when(consents)
                .requireCurrentSubject("seller");

        assertThatThrownBy(() -> service.open("buyer", "listing-1"))
                .isInstanceOf(ForbiddenException.class)
                .extracting("code")
                .isEqualTo("THIRD_PARTY_PROVISION_SUBJECT_CONSENT_REQUIRED");
        verify(listings, never()).requirePublic("listing-1");
        verify(rooms, never()).createOrGetExisting(any());
    }

    @Test
    @DisplayName("자기 매물에는 방을 열지 않는다")
    void open_rejectsOwnListing() {
        when(listings.sellerOf("listing-1")).thenReturn("same-user");

        assertThatThrownBy(() -> service.open("same-user", "listing-1")).isInstanceOf(ForbiddenException.class);
        verify(rooms, never()).createOrGetExisting(any());
    }

    @Test
    @DisplayName("공개되지 않은 매물에는 새 방을 만들지 않는다")
    void open_doesNotCreateRoomForHiddenListing() {
        when(listings.sellerOf("deleted-listing")).thenReturn("real-seller");
        when(rooms.findByParticipants("real-buyer", "real-seller", "deleted-listing"))
                .thenReturn(Optional.empty());
        doThrow(new ListingNotFoundException("deleted-listing")).when(listings).requirePublic("deleted-listing");

        assertThatThrownBy(() -> service.open("real-buyer", "deleted-listing"))
                .isInstanceOf(ListingNotFoundException.class);
        verify(rooms, never()).createOrGetExisting(any());
    }

    @Test
    @DisplayName("이미 있는 방은 매물이 숨겨진 뒤에도 동의·신원확인 없이 그대로 연다")
    void open_returnsExistingRoomAfterListingWasHidden() {
        ChatRoom existing = ChatRoom.open("room-1", "deleted-listing", "real-buyer", "real-seller", NOW);
        when(listings.sellerOf("deleted-listing")).thenReturn("real-seller");
        when(rooms.findByParticipants("real-buyer", "real-seller", "deleted-listing"))
                .thenReturn(Optional.of(existing));

        assertThat(service.open("real-buyer", "deleted-listing")).isSameAs(existing);
        verifyNoInteractions(consents);
        verifyNoInteractions(sellerVerification);
        verify(listings, never()).requirePublic("deleted-listing");
        verify(rooms, never()).createOrGetExisting(any());
    }

    @Test
    @DisplayName("내 매물 방은 최근 활동순 100개까지 읽는다")
    void myRooms_appliesLimit() {
        when(rooms.findRecentByParticipant("account-1", 100)).thenReturn(List.of());

        assertThat(service.myRooms("account-1")).isEmpty();
        verify(rooms).findRecentByParticipant("account-1", 100);
    }

    @Test
    @DisplayName("최근 목록 밖의 매물 방도 권한 확인 뒤 해석한다")
    void resolve_listingRoomOutsideRecentWindow() {
        ChatRoom listingRoom = ChatRoom.open("room-old", "listing-1", "account-1", "seller-1", NOW);
        when(socialChats.requireReadable("room-old", "account-1"))
                .thenReturn(SocialChatRoom.listing("room-old", "listing-1", "account-1", "seller-1", NOW));
        when(rooms.findById("room-old")).thenReturn(Optional.of(listingRoom));

        var resolved = service.resolve("room-old", "account-1");

        assertThat(resolved.isListing()).isTrue();
        assertThat(resolved.listingRoom()).isSameAs(listingRoom);
        assertThat(resolved.socialRoom()).isNull();
    }

    @Test
    @DisplayName("문의방은 현재 티켓 상태와 함께 해석한다")
    void resolve_supportRoomWithCurrentTicket() {
        SocialChatRoom support = SocialChatRoom.support("support-old", "account-1", "정산 문의", NOW);
        SupportTicket ticket =
                new SupportTicket("support-old", "account-1", SupportStatus.IN_PROGRESS, "admin-1", NOW, NOW, null);
        when(socialChats.requireReadable("support-old", "account-1")).thenReturn(support);
        when(socialChats.supportTicketOf("support-old")).thenReturn(Optional.of(ticket));

        var resolved = service.resolve("support-old", "account-1");

        assertThat(resolved.isListing()).isFalse();
        assertThat(resolved.socialRoom()).isSameAs(support);
        assertThat(resolved.supportTicket()).isSameAs(ticket);
    }
}
