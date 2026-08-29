package com.gole.api.chat.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** 신고 시점에 고정한 대화 문맥. 살아 있는 방 권한과 완전히 분리해 보관한다. */
@Document(collection = "chat_report_snapshots")
public class ChatReportSnapshotDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String reportId;

    private String roomId;
    private String reportedMessageId;
    private String reporterId;
    private List<SnapshotMessageDocument> messages;
    private Instant capturedAt;

    protected ChatReportSnapshotDocument() {}

    public ChatReportSnapshotDocument(
            String id,
            String reportId,
            String roomId,
            String reportedMessageId,
            String reporterId,
            List<SnapshotMessageDocument> messages,
            Instant capturedAt) {
        this.id = id;
        this.reportId = reportId;
        this.roomId = roomId;
        this.reportedMessageId = reportedMessageId;
        this.reporterId = reporterId;
        this.messages = messages;
        this.capturedAt = capturedAt;
    }

    public String getId() {
        return id;
    }

    public String getReportId() {
        return reportId;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getReportedMessageId() {
        return reportedMessageId;
    }

    public String getReporterId() {
        return reporterId;
    }

    public List<SnapshotMessageDocument> getMessages() {
        return messages;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public record SnapshotMessageDocument(String messageId, String senderId, String content, Instant sentAt) {}
}
