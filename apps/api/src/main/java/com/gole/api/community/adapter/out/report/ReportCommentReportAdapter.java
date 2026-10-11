package com.gole.api.community.adapter.out.report;

import com.gole.api.community.application.port.out.CommentReportPort;
import com.gole.api.report.application.port.in.SubmitReportUseCase;
import com.gole.api.report.application.port.in.SubmitReportUseCase.SubmitReportCommand;
import com.gole.api.report.domain.model.ReportReason;
import com.gole.api.report.domain.model.ReportTargetType;
import org.springframework.stereotype.Component;

/** 신고 컨텍스트 통합 어댑터. 댓글 신고를 report 의 접수 유스케이스로 넘긴다. */
@Component
public class ReportCommentReportAdapter implements CommentReportPort {

    private final SubmitReportUseCase reports;

    public ReportCommentReportAdapter(SubmitReportUseCase reports) {
        this.reports = reports;
    }

    @Override
    public String submit(String reporterId, String commentId, String reasonCode, String detail) {
        return reports.submit(new SubmitReportCommand(
                reporterId, ReportTargetType.COMMENT, commentId, ReportReason.valueOf(reasonCode), detail));
    }
}
