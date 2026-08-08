package com.gole.api.report.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 신고 MongoDB 도큐먼트.
 */
@Document(collection = "reports")
@CompoundIndex(
        name = "uq_pending_reporter_target",
        def = "{'reporterId': 1, 'targetType': 1, 'targetId': 1}",
        unique = true,
        partialFilter = "{'status': 'PENDING'}")
public class ReportDocument {

    @Id
    private String id;

    @Indexed
    private String reporterId;

    private String targetType;

    @Indexed
    private String targetId;

    private String reason;
    private String detail;

    @Indexed
    private String status;

    private Instant createdAt;
    private Instant handledAt;

    protected ReportDocument() {}

    public ReportDocument(
            String id,
            String reporterId,
            String targetType,
            String targetId,
            String reason,
            String detail,
            String status,
            Instant createdAt,
            Instant handledAt) {
        this.id = id;
        this.reporterId = reporterId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
        this.detail = detail;
        this.status = status;
        this.createdAt = createdAt;
        this.handledAt = handledAt;
    }

    public String getId() {
        return id;
    }

    public String getReporterId() {
        return reporterId;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getReason() {
        return reason;
    }

    public String getDetail() {
        return detail;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getHandledAt() {
        return handledAt;
    }
}
