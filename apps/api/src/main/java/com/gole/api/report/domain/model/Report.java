package com.gole.api.report.domain.model;

import com.gole.api.report.domain.exception.ReportAlreadyHandledException;
import java.time.Instant;
import java.util.Objects;

/**
 * 신고 애그리거트 — 매물/게시글에 대한 가품·IP 도용·사기 등 신고 접수와 처리 상태 전이를
 * 캡슐화한다. notice & takedown(OSP 면책) 절차의 단일 진실 공급원.
 */
public final class Report {

    private final String id;
    private final String reporterId;
    private final ReportTargetType targetType;
    private final String targetId;
    private final ReportReason reason;
    private final String detail;
    private ReportStatus status;
    private final Instant createdAt;
    private Instant handledAt;

    public Report(
            String id,
            String reporterId,
            ReportTargetType targetType,
            String targetId,
            ReportReason reason,
            String detail,
            ReportStatus status,
            Instant createdAt,
            Instant handledAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.reporterId = requireText(reporterId, "reporterId");
        this.targetType = Objects.requireNonNull(targetType, "targetType");
        this.targetId = requireText(targetId, "targetId");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.detail = detail == null ? "" : detail;
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.handledAt = handledAt;
    }

    /** 신규 신고: 접수(PENDING) 상태로 생성. */
    public static Report submit(
            String id,
            String reporterId,
            ReportTargetType targetType,
            String targetId,
            ReportReason reason,
            String detail,
            Instant now) {
        return new Report(id, reporterId, targetType, targetId, reason, detail, ReportStatus.PENDING, now, null);
    }

    /** 조치 완료 처리. PENDING 상태에서만 가능하다. */
    public void resolve(Instant now) {
        transition(ReportStatus.RESOLVED, now);
    }

    /** 기각 처리. PENDING 상태에서만 가능하다. */
    public void dismiss(Instant now) {
        transition(ReportStatus.DISMISSED, now);
    }

    private void transition(ReportStatus next, Instant now) {
        if (status != ReportStatus.PENDING) {
            throw new ReportAlreadyHandledException(id);
        }
        this.status = next;
        this.handledAt = Objects.requireNonNull(now, "now");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public String getId() {
        return id;
    }

    public String getReporterId() {
        return reporterId;
    }

    public ReportTargetType getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public ReportReason getReason() {
        return reason;
    }

    public String getDetail() {
        return detail;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getHandledAt() {
        return handledAt;
    }
}
