package com.gole.api.report.adapter.in.web;

import com.gole.api.report.domain.model.Report;
import com.gole.api.report.domain.model.ReportReason;
import com.gole.api.report.domain.model.ReportStatus;
import com.gole.api.report.domain.model.ReportTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * 신고 REST DTO 모음.
 */
public final class ReportDtos {

    private ReportDtos() {}

    public record SubmitReportRequest(
            @NotBlank String reporterId,
            @NotNull ReportTargetType targetType,
            @NotBlank String targetId,
            @NotNull ReportReason reason,
            @Size(max = 1000) String detail) {}

    public record ReportResponse(
            String id,
            String reporterId,
            ReportTargetType targetType,
            String targetId,
            ReportReason reason,
            String detail,
            ReportStatus status,
            Instant createdAt,
            Instant handledAt) {

        public static ReportResponse from(Report report) {
            return new ReportResponse(
                    report.getId(),
                    report.getReporterId(),
                    report.getTargetType(),
                    report.getTargetId(),
                    report.getReason(),
                    report.getDetail(),
                    report.getStatus(),
                    report.getCreatedAt(),
                    report.getHandledAt());
        }
    }
}
