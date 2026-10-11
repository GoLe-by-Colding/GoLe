package com.gole.api.admin.adapter.out.report;

import com.gole.api.admin.application.port.out.ReportCasePort;
import com.gole.api.report.application.port.in.ManageReportsUseCase;
import com.gole.api.report.domain.model.Report;
import com.gole.api.report.domain.model.ReportStatus;
import org.springframework.stereotype.Component;

/** 신고 컨텍스트 통합 어댑터. 신고 도메인 객체를 관리자 조치에 필요한 사실로 환원한다. */
@Component
public class ReportCaseAdapter implements ReportCasePort {

    private final ManageReportsUseCase reports;

    public ReportCaseAdapter(ManageReportsUseCase reports) {
        this.reports = reports;
    }

    @Override
    public ReportCase find(String reportId) {
        Report report = reports.get(reportId);
        return new ReportCase(
                report.getId(), report.getStatus() == ReportStatus.PENDING, kind(report), report.getTargetId());
    }

    @Override
    public void markResolved(String reportId) {
        reports.resolve(reportId);
    }

    private static TargetKind kind(Report report) {
        return switch (report.getTargetType()) {
            case LISTING -> TargetKind.LISTING;
            case POST -> TargetKind.POST;
            case COMMENT -> TargetKind.COMMENT;
            case REVIEW -> TargetKind.REVIEW;
            default -> TargetKind.OTHER;
        };
    }
}
