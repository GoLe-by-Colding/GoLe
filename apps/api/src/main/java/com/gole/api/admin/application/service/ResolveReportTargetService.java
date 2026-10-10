package com.gole.api.admin.application.service;

import com.gole.api.admin.application.port.in.ResolveReportTargetUseCase;
import com.gole.api.admin.application.port.out.ContentModerationPort;
import com.gole.api.admin.application.port.out.ReportCasePort;
import com.gole.api.admin.application.port.out.ReportCasePort.ReportCase;
import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.exception.ConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 신고 대상 조치와 신고 완료를 하나의 Mongo 트랜잭션으로 묶는다. */
@Service
public class ResolveReportTargetService implements ResolveReportTargetUseCase {

    private final ReportCasePort reports;
    private final ContentModerationPort moderation;

    public ResolveReportTargetService(ReportCasePort reports, ContentModerationPort moderation) {
        this.reports = reports;
        this.moderation = moderation;
    }

    @Override
    @Transactional
    public void resolve(String reportId, String reason) {
        ReportCase report = reports.find(reportId);
        if (!report.pending()) {
            throw new ConflictException("REPORT_ALREADY_HANDLED", "Report already handled: " + reportId);
        }
        switch (report.targetKind()) {
            case LISTING -> moderation.takedownListing(report.targetId(), reason);
            case POST -> moderation.removePost(report.targetId(), reason);
            case COMMENT -> moderation.hideComment(report.targetId(), reason);
            case REVIEW -> moderation.hideReview(report.targetId(), reason);
            case OTHER ->
                throw new BadRequestException(
                        "CHAT_REPORT_MANUAL_ACTION_REQUIRED", "채팅 신고는 스냅샷을 검토한 뒤 계정 조치 또는 단순 완료를 선택해 주세요");
        }
        reports.markResolved(reportId);
    }
}
