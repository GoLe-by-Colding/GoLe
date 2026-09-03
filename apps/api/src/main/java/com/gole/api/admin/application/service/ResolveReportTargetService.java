package com.gole.api.admin.application.service;

import com.gole.api.common.exception.BadRequestException;
import com.gole.api.community.application.port.in.ModerateCommentUseCase;
import com.gole.api.community.application.port.in.ModeratePostUseCase;
import com.gole.api.listing.application.port.in.ModerateListingUseCase;
import com.gole.api.report.application.port.in.ManageReportsUseCase;
import com.gole.api.report.domain.exception.ReportAlreadyHandledException;
import com.gole.api.report.domain.model.Report;
import com.gole.api.report.domain.model.ReportStatus;
import com.gole.api.report.domain.model.ReportTargetType;
import com.gole.api.review.application.port.in.ModerateReviewUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 신고 대상 조치와 신고 완료를 하나의 Mongo 트랜잭션으로 묶는다. */
@Service
public class ResolveReportTargetService {

    private final ManageReportsUseCase reports;
    private final ModerateListingUseCase listings;
    private final ModeratePostUseCase posts;
    private final ModerateCommentUseCase comments;
    private final ModerateReviewUseCase reviews;

    public ResolveReportTargetService(
            ManageReportsUseCase reports,
            ModerateListingUseCase listings,
            ModeratePostUseCase posts,
            ModerateCommentUseCase comments,
            ModerateReviewUseCase reviews) {
        this.reports = reports;
        this.listings = listings;
        this.posts = posts;
        this.comments = comments;
        this.reviews = reviews;
    }

    @Transactional
    public Report resolve(String reportId, String reason) {
        Report report = reports.get(reportId);
        if (report.getStatus() != ReportStatus.PENDING) {
            throw new ReportAlreadyHandledException(reportId);
        }
        if (report.getTargetType() == ReportTargetType.LISTING) {
            listings.takedown(report.getTargetId(), reason);
        } else if (report.getTargetType() == ReportTargetType.POST) {
            posts.removeByModerator(report.getTargetId(), reason);
        } else if (report.getTargetType() == ReportTargetType.COMMENT) {
            comments.hide(report.getTargetId(), reason);
        } else if (report.getTargetType() == ReportTargetType.REVIEW) {
            reviews.hide(report.getTargetId(), reason);
        } else {
            throw new BadRequestException(
                    "CHAT_REPORT_MANUAL_ACTION_REQUIRED", "채팅 신고는 스냅샷을 검토한 뒤 계정 조치 또는 단순 완료를 선택해 주세요");
        }
        return reports.resolve(reportId);
    }
}
