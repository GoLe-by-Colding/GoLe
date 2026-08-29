package com.gole.api.chat.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "support_tickets")
@CompoundIndex(name = "support_status_updated_idx", def = "{'status': 1, 'updatedAt': -1}")
public class SupportTicketDocument {

    @Id
    private String roomId;

    private String requesterId;
    private String status;
    private String assigneeId;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant resolvedAt;

    @Version
    private long version;

    protected SupportTicketDocument() {}

    public SupportTicketDocument(
            String roomId,
            String requesterId,
            String status,
            String assigneeId,
            Instant createdAt,
            Instant updatedAt,
            Instant resolvedAt,
            long version) {
        this.roomId = roomId;
        this.requesterId = requesterId;
        this.status = status;
        this.assigneeId = assigneeId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.resolvedAt = resolvedAt;
        this.version = version;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getRequesterId() {
        return requesterId;
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
}
