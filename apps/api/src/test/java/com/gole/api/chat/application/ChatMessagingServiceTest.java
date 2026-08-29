package com.gole.api.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.chat.adapter.out.persistence.ChatMessageDocument;
import com.gole.api.chat.adapter.out.persistence.ChatMessageMongoRepository;
import com.gole.api.chat.adapter.out.pubsub.ChatRedisPublisher;
import com.gole.api.common.exception.BadRequestException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class ChatMessagingServiceTest {

    private final ChatMessageMongoRepository messages = mock(ChatMessageMongoRepository.class);
    private final ChatRedisPublisher publisher = mock(ChatRedisPublisher.class);
    private final SocialChatService socialChats = mock(SocialChatService.class);
    private final ChatMessagingService service = new ChatMessagingService(
            messages, publisher, socialChats, Clock.fixed(Instant.parse("2026-08-30T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void history_returnsOlderPageInChronologicalOrder() {
        Instant cursorTime = Instant.parse("2026-08-30T00:03:00Z");
        ChatMessageDocument older = message("m1", "2026-08-30T00:01:00Z");
        ChatMessageDocument newer = message("m2", "2026-08-30T00:02:00Z");
        when(messages.findContextBefore(eq("room-1"), eq(cursorTime), eq("m3"), any(Pageable.class)))
                .thenReturn(List.of(newer, older));

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
        ChatMessageDocument cursor = message("m1", "2026-08-30T00:01:00Z");
        ChatMessageDocument next = message("m2", "2026-08-30T00:02:00Z");
        when(messages.findById("m1")).thenReturn(Optional.of(cursor));
        when(messages.findContextAfter(eq("room-1"), eq(cursor.getSentAt()), eq("m1"), any(Pageable.class)))
                .thenReturn(List.of(next));

        var replay = service.after("room-1", "account-1", "m1", 200);

        assertThat(replay).extracting(message -> message.id()).containsExactly("m2");
    }

    @Test
    void after_rejectsCursorFromAnotherRoom() {
        when(messages.findById("foreign"))
                .thenReturn(Optional.of(new ChatMessageDocument(
                        "foreign", "room-2", "account-2", "foreign", Instant.parse("2026-08-30T00:01:00Z"))));

        assertThatThrownBy(() -> service.after("room-1", "account-1", "foreign", 200))
                .isInstanceOf(BadRequestException.class);
    }

    private static ChatMessageDocument message(String id, String sentAt) {
        return new ChatMessageDocument(id, "room-1", "account-1", id, Instant.parse(sentAt));
    }
}
