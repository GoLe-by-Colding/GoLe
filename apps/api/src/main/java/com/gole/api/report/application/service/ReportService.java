package com.gole.api.report.application.service;

import com.gole.api.report.application.port.in.ManageReportsUseCase;
import com.gole.api.report.application.port.in.SubmitReportUseCase;
import com.gole.api.report.application.port.out.ReportIdGeneratorPort;
import com.gole.api.report.application.port.out.ReportRepositoryPort;
import com.gole.api.report.domain.exception.DuplicateReportException;
import com.gole.api.report.domain.exception.ReportNotFoundException;
import com.gole.api.report.domain.model.Report;
import com.gole.api.report.domain.model.ReportStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 신고 애플리케이션 서비스 — 접수(중복 방지)와 운영자 처리(조치/기각)를 오케스트레이션한다.
 */
@Service
public class ReportService implements SubmitReportUseCase, ManageReportsUseCase {

    private final ReportRepositoryPort reportRepository;
    private final ReportIdGeneratorPort idGenerator;
    private final Clock clock;

    public ReportService(ReportRepositoryPort reportRepository, ReportIdGeneratorPort idGenerator, Clock clock) {
        this.reportRepository = reportRepository;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    public String submit(SubmitReportCommand command) {
        // 같은 사용자가 같은 대상에 미처리 신고를 중복 접수하는 것을 방지한다.
        if (reportRepository.existsPendingByReporterAndTarget(
                command.reporterId(), command.targetType(), command.targetId())) {
            throw new DuplicateReportException(command.targetId());
        }
        Report report = Report.submit(
                idGenerator.newId(),
                command.reporterId(),
                command.targetType(),
                command.targetId(),
                command.reason(),
                command.detail(),
                Instant.now(clock));
        return reportRepository.save(report).getId();
    }

    @Override
    public List<Report> list(ReportStatus status, int limit) {
        return reportRepository.findRecentFirst(status, limit);
    }

    @Override
    public Report resolve(String reportId) {
        Report report = getOrThrow(reportId);
        report.resolve(Instant.now(clock));
        return reportRepository.save(report);
    }

    @Override
    public Report dismiss(String reportId) {
        Report report = getOrThrow(reportId);
        report.dismiss(Instant.now(clock));
        return reportRepository.save(report);
    }

    private Report getOrThrow(String reportId) {
        return reportRepository.findById(reportId).orElseThrow(() -> new ReportNotFoundException(reportId));
    }
}
