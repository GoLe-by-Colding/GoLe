package com.gole.api.report.application.port.out;

import com.gole.api.report.domain.model.Report;
import com.gole.api.report.domain.model.ReportStatus;
import com.gole.api.report.domain.model.ReportTargetType;
import java.util.List;
import java.util.Optional;

/**
 * 신고 영속성 출력 포트.
 */
public interface ReportRepositoryPort {

    Report save(Report report);

    Optional<Report> findById(String reportId);

    List<Report> findRecentFirst(ReportStatus status, int limit);

    boolean existsPendingByReporterAndTarget(String reporterId, ReportTargetType targetType, String targetId);

    long countByStatus(ReportStatus status);
}
