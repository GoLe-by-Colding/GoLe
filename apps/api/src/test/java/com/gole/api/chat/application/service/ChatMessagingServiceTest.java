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
import com.gole.api.chat.application.port.out.ChatMessagePublisherPort;
import com.gole.api.chat.application.port.out.ChatMessageRepositoryPort;
import com.gole.api.chat.domain.model.ChatMessage;
import com.gole.api.chat.domain.model.SocialChatRoom;
import com.gole.api.chat.domain.model.SupportCategory;
import com.gole.api.chat.domain.model.SupportTicket;
import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.exception.ForbiddenException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatMessagingServiceTest {

    private final ChatMessageRepositoryPort messages = mock(ChatMessageRepositoryPort.class);
    private final ChatMessagePublisherPort publisher = mock(ChatMessagePublisherPort.class);
    private final SocialChatService socialChats = mock(SocialChatService.class);
    private final ChatConsentPort consents = mock(ChatConsentPort.class);
    private final SupportOperationalEventNotifier supportEvents = mock(SupportOperationalEventNotifier.class);
    private final SupportAssistantAnalysisService supportAnalysis = mock(SupportAssistantAnalysisService.class);
    private final ChatMessagingService service = new ChatMessagingService(
            messages,
            publisher,
            socialChats,
            consents,
            supportEvents,
            supportAnalysis,
            Clock.fixed(Instant.parse("2026-08-30T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void history_returnsOlderPageInChronologicalOrder() {
        Instant cursorTime = Instant.parse("2026-08-30T00:03:00Z");
        ChatMessage older = message("m1", "2026-08-30T00:01:00Z");
        ChatMessage newer = message("m2", "2026-08-30T00:02:00Z");
        when(messages.findBefore("room-1", cursorTime, "m3", 60)).thenReturn(List.of(newer, older));

        var page = service.history("room-1", "account-1", cursorTime, "m3", 60);

        assertThat(page).extracting(message -> message.id()).containsExactly("m1", "m2");
        verify(socialChats).requireReadable("room-1", "account-1");
    }

    @Test
    void history_rejectsHalfCursor() {
        assertThatThrownBy(
                        () -> service.history("room-1", "account-1", Instant.parse("2026-08-30T00:03:00Z"), null, 60))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void after_replaysOnlyMessagesFromCursorRoom() {
        ChatMessage cursor = message("m1", "2026-08-30T00:01:00Z");
        ChatMessage next = message("m2", "2026-08-30T00:02:00Z");
        when(messages.findById("m1")).thenReturn(Optional.of(cursor));
        when(messages.findAfter("room-1", cursor.sentAt(), "m1", 200)).thenReturn(List.of(next));

        var replay = service.after("room-1", "account-1", "m1", 200);

        assertThat(replay).extracting(message -> message.id()).containsExactly("m2");
    }

    @Test
    void after_rejectsCursorFromAnotherRoom() {
        when(messages.findById("foreign"))
                .thenReturn(Optional.of(new ChatMessage(
                        "foreign", "room-2", "account-2", "foreign", Instant.parse("2026-08-30T00:01:00Z"))));

        assertThatThrownBy(() -> service.after("room-1", "account-1", "foreign", 200))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void supportOpeningPublishesOnlyOpenedEvent() {
        Instant now = Instant.parse("2026-08-30T00:00:00Z");
        SocialChatRoom room = SocialChatRoom.support("room-1", "account-1", "private title", now);
        SupportTicket ticket = SupportTicket.opened("room-1", "account-1", SupportCategory.PRODUCT_FEEDBACK, now);
        when(socialChats.requireSendable("room-1", "account-1")).thenReturn(room);
        when(messages.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(socialChats.onMessageSent(room, "account-1")).thenReturn(Optional.of(ticket));

        service.sendSupportOpening("room-1", "account-1", "private first message");

        verify(supportEvents).opened(ticket);
        verify(supportEvents, never()).requesterReplied(any());
        verify(supportAnalysis).analyzeOpeningAfterCommit(ticket, "private title", "private first message", "ko-KR");
    }

    @Test
    void requesterSupportMessagePublishesReplyEvent() {
        Instant now = Instant.parse("2026-08-30T00:00:00Z");
        SocialChatRoom room = SocialChatRoom.support("room-2", "account-1", "private title", now);
        SupportTicket ticket = SupportTicket.opened("room-2", "account-1", SupportCategory.GENERAL, now);
        when(socialChats.requireSendable("room-2", "account-1")).thenReturn(room);
        when(messages.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(socialChats.onMessageSent(room, "account-1")).thenReturn(Optional.of(ticket));

        service.send("room-2", "account-1", "private follow-up message");

        verify(supportEvents).requesterReplied(ticket);
        verify(supportEvents, never()).opened(any());
        verify(supportAnalysis, never()).analyzeOpeningAfterCommit(any(), any(), any(), any());
    }

    @Test
    @DisplayName("매물·1:1 방에 보내는 사용자 메시지는 보내는 사람의 현재 동의가 없으면 저장하지 않는다")
    void sendFromUser_requiresConsentOutsideSupportRooms() {
        SocialChatRoom listingRoom = SocialChatRoom.listing(
                "listing-room", "listing-1", "buyer", "seller", Instant.parse("2026-08-30T00:00:00Z"));
        when(socialChats.requireReadable("listing-room", "buyer")).thenReturn(listingRoom);
        doThrow(new ForbiddenException("THIRD_PARTY_PROVISION_CONSENT_REQUIRED", "consent required"))
                .when(consents)
                .requireCurrent("buyer");

        assertThatThrownBy(() -> service.sendFromUser("listing-room", "buyer", "new message"))
                .isInstanceOf(ForbiddenException.class);
        verify(messages, never()).save(any());
    }

    @Test
    @DisplayName("운영팀 문의방 메시지는 동의를 묻지 않는다")
    void sendFromUser_skipsConsentInSupportRoom() {
        Instant now = Instant.parse("2026-08-30T00:00:00Z");
        SocialChatRoom supportRoom = SocialChatRoom.support("support-room", "buyer", "privacy request", now);
        when(socialChats.requireReadable("support-room", "buyer")).thenReturn(supportRoom);
        when(socialChats.requireSendable("support-room", "buyer")).thenReturn(supportRoom);
        when(messages.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.sendFromUser("support-room", "buyer", "help").content())
                .isEqualTo("help");
        verifyNoInteractions(consents);
    }

    private static ChatMessage message(String id, String sentAt) {
        return new ChatMessage(id, "room-1", "account-1", id, Instant.parse(sentAt));
    }
}
