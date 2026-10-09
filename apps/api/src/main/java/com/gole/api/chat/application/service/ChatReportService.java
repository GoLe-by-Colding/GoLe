package com.gole.api.chat.application.service;

import com.gole.api.chat.application.port.out.ChatMessageRepositoryPort;
import com.gole.api.chat.application.port.out.ChatReportSnapshotPort;
import com.gole.api.chat.application.port.out.ChatReportSnapshotPort.Snapshot;
import com.gole.api.chat.application.port.out.ChatReportSnapshotPort.SnapshotMessage;
import com.gole.api.chat.domain.model.ChatMessage;
import com.gole.api.common.exception.NotFoundException;
import com.gole.api.report.application.port.in.SubmitReportUseCase;
import com.gole.api.report.application.port.in.SubmitReportUseCase.SubmitReportCommand;
import com.gole.api.report.domain.model.ReportReason;
import com.gole.api.report.domain.model.ReportTargetType;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 메시지 신고와 최소 문맥 스냅샷을 한 트랜잭션으로 고정한다. */
@Service
public class ChatReportService {

    private static final int CONTEXT_BEFORE = 10;
    private static final int CONTEXT_AFTER = 10;

    private final ChatMessageRepositoryPort messages;
    private final SocialChatService socialChats;
    private final SubmitReportUseCase reports;
    private final ChatReportSnapshotPort snapshots;
    private final Clock clock;

    public ChatReportService(
            ChatMessageRepositoryPort messages,
            SocialChatService socialChats,
            SubmitReportUseCase reports,
            ChatReportSnapshotPort snapshots,
            Clock clock) {
        this.messages = messages;
        this.socialChats = socialChats;
        this.reports = reports;
        this.snapshots = snapshots;
        this.clock = clock;
    }

    @Transactional
    public String report(String reporterId, String messageId, ReportReason reason, String detail) {
        ChatMessage reported = messages.findById(messageId)
                .orElseThrow(() -> new NotFoundException("CHAT_MESSAGE_NOT_FOUND", "신고할 메시지를 찾을 수 없습니다"));
        socialChats.requireReadable(reported.roomId(), reporterId);

        String reportId = reports.submit(
                new SubmitReportCommand(reporterId, ReportTargetType.CHAT_MESSAGE, messageId, reason, detail));
        snapshots.capture(new Snapshot(
                reportId, reported.roomId(), messageId, reporterId, contextAround(reported), Instant.now(clock)));
        return reportId;
    }

    private List<SnapshotMessage> contextAround(ChatMessage reported) {
        List<ChatMessage> before = new ArrayList<>(
                messages.findBefore(reported.roomId(), reported.sentAt(), reported.id(), CONTEXT_BEFORE));
        // 저장소는 가까운 메시지부터 역순으로 반환하므로 스냅샷은 다시 시간순으로 만든다.
        Collections.reverse(before);
        List<ChatMessage> chronological = new ArrayList<>(CONTEXT_BEFORE + CONTEXT_AFTER + 1);
        chronological.addAll(before);
        chronological.add(reported);
        chronological.addAll(messages.findAfter(reported.roomId(), reported.sentAt(), reported.id(), CONTEXT_AFTER));

        return chronological.stream()
                .map(message ->
                        new SnapshotMessage(message.id(), message.senderId(), message.content(), message.sentAt()))
                .toList();
    }
}
