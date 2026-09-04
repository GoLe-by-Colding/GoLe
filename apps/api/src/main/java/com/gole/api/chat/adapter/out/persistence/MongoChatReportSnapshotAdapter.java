package com.gole.api.chat.adapter.out.persistence;

import com.gole.api.chat.adapter.out.persistence.ChatReportSnapshotDocument.SnapshotMessageDocument;
import com.gole.api.chat.application.port.out.ChatReportSnapshotPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Component
public class MongoChatReportSnapshotAdapter implements ChatReportSnapshotPort {

    private final ChatReportSnapshotMongoRepository snapshots;
    private final MongoTemplate mongoTemplate;

    public MongoChatReportSnapshotAdapter(ChatReportSnapshotMongoRepository snapshots, MongoTemplate mongoTemplate) {
        this.snapshots = snapshots;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public String capture(Snapshot snapshot) {
        // SUPPORT 신고 생성과 파기가 같은 티켓 문서에 쓰도록 해 snapshot-isolation write skew를 막는다.
        // 다른 채팅 유형은 support ticket이 없으므로 matched=0이어도 기존 경로를 그대로 쓴다.
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(snapshot.roomId())),
                new Update().set("reportSnapshotFenceAt", snapshot.capturedAt()),
                SupportTicketDocument.class);
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

    @Override
    public boolean existsByRoomId(String roomId) {
        return snapshots.existsByRoomId(roomId);
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
