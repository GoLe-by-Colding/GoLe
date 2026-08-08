package com.gole.api.admin.application.service;

import com.gole.api.community.application.port.in.ModeratePostUseCase;
import com.gole.api.listing.application.port.in.ModerateListingUseCase;
import com.gole.api.report.application.port.in.ManageReportsUseCase;
import com.gole.api.report.domain.exception.ReportAlreadyHandledException;
import com.gole.api.report.domain.model.Report;
import com.gole.api.report.domain.model.ReportStatus;
import com.gole.api.report.domain.model.ReportTargetType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 신고 대상 조치와 신고 완료를 하나의 Mongo 트랜잭션으로 묶는다. */
@Service
public class ResolveReportTargetService {

    private final ManageReportsUseCase reports;
    private final ModerateListingUseCase listings;
    private final ModeratePostUseCase posts;

    public ResolveReportTargetService(
            ManageReportsUseCase reports, ModerateListingUseCase listings, ModeratePostUseCase posts) {
        this.reports = reports;
        this.listings = listings;
        this.posts = posts;
    }

    @Transactional
    public Report resolve(String reportId, String reason) {
        Report report = reports.get(reportId);
        if (report.getStatus() != ReportStatus.PENDING) {
            throw new ReportAlreadyHandledException(reportId);
        }
        if (report.getTargetType() == ReportTargetType.LISTING) {
            listings.takedown(report.getTargetId(), reason);
        } else {
            posts.removeByModerator(report.getTargetId(), reason);
        }
        return reports.resolve(reportId);
    }
}
