package com.gole.api.chat.application;

import com.gole.api.chat.adapter.out.persistence.ChatMessageDocument;
import com.gole.api.chat.adapter.out.persistence.ChatMessageMongoRepository;
import com.gole.api.chat.application.port.out.ChatReportSnapshotPort;
import com.gole.api.chat.application.port.out.ChatReportSnapshotPort.Snapshot;
import com.gole.api.chat.application.port.out.ChatReportSnapshotPort.SnapshotMessage;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 메시지 신고와 최소 문맥 스냅샷을 한 트랜잭션으로 고정한다. */
@Service
public class ChatReportService {

    private static final int CONTEXT_BEFORE = 10;
    private static final int CONTEXT_AFTER = 10;

    private final ChatMessageMongoRepository messages;
    private final SocialChatService socialChats;
    private final SubmitReportUseCase reports;
    private final ChatReportSnapshotPort snapshots;
    private final Clock clock;

    public ChatReportService(
            ChatMessageMongoRepository messages,
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
        ChatMessageDocument reported = messages.findById(messageId)
                .orElseThrow(() -> new NotFoundException("CHAT_MESSAGE_NOT_FOUND", "신고할 메시지를 찾을 수 없습니다"));
        socialChats.requireReadable(reported.getRoomId(), reporterId);

        String reportId = reports.submit(
                new SubmitReportCommand(reporterId, ReportTargetType.CHAT_MESSAGE, messageId, reason, detail));
        snapshots.capture(new Snapshot(
                reportId, reported.getRoomId(), messageId, reporterId, contextAround(reported), Instant.now(clock)));
        return reportId;
    }

    private List<SnapshotMessage> contextAround(ChatMessageDocument reported) {
        PageRequest beforePage =
                PageRequest.of(0, CONTEXT_BEFORE, Sort.by(Sort.Order.desc("sentAt"), Sort.Order.desc("id")));
        PageRequest afterPage =
                PageRequest.of(0, CONTEXT_AFTER, Sort.by(Sort.Order.asc("sentAt"), Sort.Order.asc("id")));

        List<ChatMessageDocument> before = new ArrayList<>(
                messages.findContextBefore(reported.getRoomId(), reported.getSentAt(), reported.getId(), beforePage));
        // 저장소는 가까운 메시지부터 역순으로 반환하므로 스냅샷은 다시 시간순으로 만든다.
        Collections.reverse(before);
        List<ChatMessageDocument> chronological = new ArrayList<>(CONTEXT_BEFORE + CONTEXT_AFTER + 1);
        chronological.addAll(before);
        chronological.add(reported);
        chronological.addAll(
                messages.findContextAfter(reported.getRoomId(), reported.getSentAt(), reported.getId(), afterPage));

        return chronological.stream()
                .map(message -> new SnapshotMessage(
                        message.getId(), message.getSenderId(), message.getContent(), message.getSentAt()))
                .toList();
    }
}
