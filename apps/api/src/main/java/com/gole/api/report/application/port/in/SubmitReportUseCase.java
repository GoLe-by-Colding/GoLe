package com.gole.api.report.application.port.in;

import com.gole.api.report.domain.model.ReportReason;
import com.gole.api.report.domain.model.ReportTargetType;

/**
 * 신고 접수 유스케이스 — 매물/게시글에 대한 가품·IP 도용·사기 신고.
 */
public interface SubmitReportUseCase {

    String submit(SubmitReportCommand command);

    record SubmitReportCommand(
            String reporterId, ReportTargetType targetType, String targetId, ReportReason reason, String detail) {}
}
