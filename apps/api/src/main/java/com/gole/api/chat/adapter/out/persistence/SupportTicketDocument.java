package com.gole.api.chat.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "support_tickets")
@CompoundIndexes({
    @CompoundIndex(name = "support_status_updated_idx", def = "{'status': 1, 'updatedAt': -1}"),
    @CompoundIndex(name = "support_requester_updated_idx", def = "{'requesterId': 1, 'updatedAt': -1}"),
    @CompoundIndex(name = "support_assignee_updated_idx", def = "{'assigneeId': 1, 'updatedAt': -1}"),
    @CompoundIndex(name = "support_created_idx", def = "{'createdAt': 1, '_id': 1}")
})
public class SupportTicketDocument {

    @Id
    private String roomId;

    private String requesterId;
    private String category;
    private String status;
    private String assigneeId;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant resolvedAt;
    private List<NoticeDocument> notices;

    @Version
    private long version;

    protected SupportTicketDocument() {}

    public SupportTicketDocument(
            String roomId,
            String requesterId,
            String category,
            String status,
            String assigneeId,
            Instant createdAt,
            Instant updatedAt,
            Instant resolvedAt,
            long version) {
        this(roomId, requesterId, category, status, assigneeId, createdAt, updatedAt, resolvedAt, version, List.of());
    }

    public SupportTicketDocument(
            String roomId,
            String requesterId,
            String category,
            String status,
            String assigneeId,
            Instant createdAt,
            Instant updatedAt,
            Instant resolvedAt,
            long version,
            List<NoticeDocument> notices) {
        this.roomId = roomId;
        this.requesterId = requesterId;
        this.category = category;
        this.status = status;
        this.assigneeId = assigneeId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.resolvedAt = resolvedAt;
        this.version = version;
        this.notices = notices;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getRequesterId() {
        return requesterId;
    }

    public String getCategory() {
        return category;
    }

    public String getStatus() {
        return status;
    }

    public String getAssigneeId() {
        return assigneeId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public long getVersion() {
        return version;
    }

    public List<NoticeDocument> getNotices() {
        return notices == null ? List.of() : notices;
    }

    public record NoticeDocument(String kind, String messageId, String senderId, Instant sentAt) {}
}
