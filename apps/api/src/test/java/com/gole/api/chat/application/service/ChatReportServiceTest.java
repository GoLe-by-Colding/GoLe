package com.gole.api.chat.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.chat.application.port.out.ChatMessageReportPort;
import com.gole.api.chat.application.port.out.ChatMessageRepositoryPort;
import com.gole.api.chat.application.port.out.ChatReportSnapshotPort;
import com.gole.api.chat.application.port.out.ChatReportSnapshotPort.Snapshot;
import com.gole.api.chat.domain.model.ChatMessage;
import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.common.exception.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;

class ChatReportServiceTest {

    private static final Instant START = Instant.parse("2026-08-29T10:00:00Z");
    private static final Instant CAPTURED_AT = Instant.parse("2026-08-29T12:00:00Z");

    private final ChatMessageRepositoryPort messages = mock(ChatMessageRepositoryPort.class);
    private final SocialChatService socialChats = mock(SocialChatService.class);
    private final ChatMessageReportPort reports = mock(ChatMessageReportPort.class);
    private final ChatReportSnapshotPort snapshots = mock(ChatReportSnapshotPort.class);
    private final ChatReportService service =
            new ChatReportService(messages, socialChats, reports, snapshots, Clock.fixed(CAPTURED_AT, ZoneOffset.UTC));

    @Test
    void capturesServerSideChronologicalContextAndBindsItToCreatedReport() {
        List<ChatMessage> roomMessages =
                IntStream.range(0, 25).mapToObj(this::message).toList();
        ChatMessage reported = roomMessages.get(12);
        when(messages.findById("message-12")).thenReturn(Optional.of(reported));
        when(messages.findBefore(
                        ArgumentMatchers.eq("room-1"),
                        ArgumentMatchers.eq(reported.sentAt()),
                        ArgumentMatchers.eq("message-12"),
                        ArgumentMatchers.anyInt()))
                .thenReturn(roomMessages.subList(2, 12).reversed());
        when(messages.findAfter(
                        ArgumentMatchers.eq("room-1"),
                        ArgumentMatchers.eq(reported.sentAt()),
                        ArgumentMatchers.eq("message-12"),
                        ArgumentMatchers.anyInt()))
                .thenReturn(roomMessages.subList(13, 23));
        when(reports.submit(any(), any(), any(), any())).thenReturn("report-1");

        String reportId = service.report("reporter-1", "message-12", "INAPPROPRIATE", "욕설 메시지");

        assertThat(reportId).isEqualTo("report-1");
        verify(socialChats).requireReadable("room-1", "reporter-1");

        verify(reports).submit("reporter-1", "message-12", "INAPPROPRIATE", "욕설 메시지");

        ArgumentCaptor<Snapshot> snapshot = ArgumentCaptor.forClass(Snapshot.class);
        verify(snapshots).capture(snapshot.capture());
        assertThat(snapshot.getValue().reportId()).isEqualTo("report-1");
        assertThat(snapshot.getValue().roomId()).isEqualTo("room-1");
        assertThat(snapshot.getValue().reportedMessageId()).isEqualTo("message-12");
        assertThat(snapshot.getValue().reporterId()).isEqualTo("reporter-1");
        assertThat(snapshot.getValue().capturedAt()).isEqualTo(CAPTURED_AT);
        assertThat(snapshot.getValue().messages())
                .extracting(ChatReportSnapshotPort.SnapshotMessage::messageId)
                .containsExactly(IntStream.rangeClosed(2, 22)
                        .mapToObj(index -> "message-" + index)
                        .toArray(String[]::new));
        assertThat(snapshot.getValue().messages().get(10).content()).isEqualTo("server-content-12");

        InOrder persistedInOrder = inOrder(reports, snapshots);
        persistedInOrder.verify(reports).submit(any(), any(), any(), any());
        persistedInOrder.verify(snapshots).capture(any());
    }

    @Test
    void oldReportedMessageOnlyCapturesItsActualNeighbours() {
        ChatMessage reported = message(100);
        when(messages.findById("message-100")).thenReturn(Optional.of(reported));
        when(messages.findBefore(
                        ArgumentMatchers.eq("room-1"),
                        ArgumentMatchers.eq(reported.sentAt()),
                        ArgumentMatchers.eq("message-100"),
                        ArgumentMatchers.anyInt()))
                .thenReturn(List.of(message(99), message(98)));
        when(messages.findAfter(
                        ArgumentMatchers.eq("room-1"),
                        ArgumentMatchers.eq(reported.sentAt()),
                        ArgumentMatchers.eq("message-100"),
                        ArgumentMatchers.anyInt()))
                .thenReturn(List.of(message(101), message(102)));
        when(reports.submit(any(), any(), any(), any())).thenReturn("report-2");

        service.report("reporter-1", "message-100", "OTHER", null);

        ArgumentCaptor<Snapshot> snapshot = ArgumentCaptor.forClass(Snapshot.class);
        verify(snapshots).capture(snapshot.capture());
        assertThat(snapshot.getValue().messages())
                .extracting(ChatReportSnapshotPort.SnapshotMessage::messageId)
                .containsExactly("message-98", "message-99", "message-100", "message-101", "message-102");
    }

    @Test
    void rejectsReporterWithoutRoomAccessBeforeCreatingReportOrSnapshot() {
        ChatMessage reported = message(4);
        when(messages.findById("message-4")).thenReturn(Optional.of(reported));
        when(socialChats.requireReadable("room-1", "outsider"))
                .thenThrow(new ForbiddenException("CHAT_ROOM_ACCESS_DENIED", "채팅방 멤버만 접근할 수 있습니다"));

        assertThatThrownBy(() -> service.report("outsider", "message-4", "INAPPROPRIATE", "신고"))
                .isInstanceOf(ForbiddenException.class);

        verify(reports, never()).submit(any(), any(), any(), any());
        verify(messages, never()).findBefore(any(), any(), any(), ArgumentMatchers.anyInt());
        verify(messages, never()).findAfter(any(), any(), any(), ArgumentMatchers.anyInt());
        verify(snapshots, never()).capture(any());
    }

    @Test
    void missingMessageCannotCreateAnUnboundReport() {
        when(messages.findById("missing-message")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.report("reporter-1", "missing-message", "FRAUD", "삭제된 메시지"))
                .isInstanceOf(NotFoundException.class);

        verify(socialChats, never()).requireReadable(any(), any());
        verify(reports, never()).submit(any(), any(), any(), any());
        verify(snapshots, never()).capture(any());
    }

    private ChatMessage message(int index) {
        return new ChatMessage(
                "message-" + index,
                "room-1",
                "sender-" + index,
                "server-content-" + index,
                START.plus(index, ChronoUnit.MINUTES));
    }
}
