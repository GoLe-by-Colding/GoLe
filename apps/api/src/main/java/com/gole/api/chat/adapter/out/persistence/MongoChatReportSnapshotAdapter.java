package com.gole.api.chat.adapter.out.persistence;

import com.gole.api.chat.adapter.out.persistence.ChatReportSnapshotDocument.SnapshotMessageDocument;
import com.gole.api.chat.application.port.out.ChatReportSnapshotPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MongoChatReportSnapshotAdapter implements ChatReportSnapshotPort {

    private final ChatReportSnapshotMongoRepository snapshots;

    public MongoChatReportSnapshotAdapter(ChatReportSnapshotMongoRepository snapshots) {
        this.snapshots = snapshots;
    }

    @Override
    public String capture(Snapshot snapshot) {
        ChatReportSnapshotDocument saved = snapshots.save(new ChatReportSnapshotDocument(
                UUID.randomUUID().toString(),
                snapshot.reportId(),
                snapshot.roomId(),
                snapshot.reportedMessageId(),
                snapshot.reporterId(),
                snapshot.messages().stream()
                        .map(message -> new SnapshotMessageDocument(
                                message.messageId(), message.senderId(), message.content(), message.sentAt()))
                        .toList(),
                snapshot.capturedAt()));
        return saved.getId();
    }

    @Override
    public Optional<StoredSnapshot> findByReportId(String reportId) {
        return snapshots.findByReportId(reportId).map(MongoChatReportSnapshotAdapter::toStored);
    }

    private static StoredSnapshot toStored(ChatReportSnapshotDocument document) {
        return new StoredSnapshot(
                document.getId(),
                document.getReportId(),
                document.getRoomId(),
                document.getReportedMessageId(),
                document.getReporterId(),
                document.getMessages().stream()
                        .map(message -> new SnapshotMessage(
                                message.messageId(), message.senderId(), message.content(), message.sentAt()))
                        .toList(),
                document.getCapturedAt());
    }
}
