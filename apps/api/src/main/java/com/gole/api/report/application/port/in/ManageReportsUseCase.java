package com.gole.api.report.application.port.in;

import com.gole.api.report.domain.model.Report;
import com.gole.api.report.domain.model.ReportStatus;
import java.util.List;

/**
 * 신고 처리 유스케이스(운영자 전용) — 접수 큐 조회·조치 완료·기각.
 */
public interface ManageReportsUseCase {

    List<Report> list(ReportStatus status, int limit);

    long count(ReportStatus status);

    Report get(String reportId);

    Report resolve(String reportId);

    Report dismiss(String reportId);
}
