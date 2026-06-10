package com.gole.api.report.adapter.out.persistence;

import com.gole.api.report.application.port.out.ReportRepositoryPort;
import com.gole.api.report.domain.model.Report;
import com.gole.api.report.domain.model.ReportReason;
import com.gole.api.report.domain.model.ReportStatus;
import com.gole.api.report.domain.model.ReportTargetType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * 신고 영속성 어댑터. 도메인 {@link Report}와 {@link ReportDocument}를 양방향 매핑한다.
 */
@Component
public class ReportPersistenceAdapter implements ReportRepositoryPort {

    private final ReportMongoRepository repository;

    public ReportPersistenceAdapter(ReportMongoRepository repository) {
        this.repository = repository;
    }

    @Override
    public Report save(Report report) {
        ReportDocument saved = repository.save(toDocument(report));
        return toDomain(saved);
    }

    @Override
    public Optional<Report> findById(String reportId) {
        return repository.findById(reportId).map(this::toDomain);
    }

    @Override
    public List<Report> findRecentFirst(ReportStatus status, int limit) {
        PageRequest page = PageRequest.of(0, Math.max(1, limit));
        List<ReportDocument> documents = status == null
                ? repository.findAllByOrderByCreatedAtDesc(page)
                : repository.findByStatusOrderByCreatedAtDesc(status.name(), page);
        return documents.stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsPendingByReporterAndTarget(String reporterId, ReportTargetType targetType, String targetId) {
        return repository.existsByReporterIdAndTargetTypeAndTargetIdAndStatus(
                reporterId, targetType.name(), targetId, ReportStatus.PENDING.name());
    }

    @Override
    public long countByStatus(ReportStatus status) {
        return repository.countByStatus(status.name());
    }

    private ReportDocument toDocument(Report report) {
        return new ReportDocument(
                report.getId(),
                report.getReporterId(),
                report.getTargetType().name(),
                report.getTargetId(),
                report.getReason().name(),
                report.getDetail(),
                report.getStatus().name(),
                report.getCreatedAt(),
                report.getHandledAt());
    }

    private Report toDomain(ReportDocument document) {
        return new Report(
                document.getId(),
                document.getReporterId(),
                ReportTargetType.valueOf(document.getTargetType()),
                document.getTargetId(),
                ReportReason.valueOf(document.getReason()),
                document.getDetail(),
                ReportStatus.valueOf(document.getStatus()),
                document.getCreatedAt(),
                document.getHandledAt());
    }
}
