package com.gole.api.admin.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.admin.adapter.in.web.AdminDtos.ReasonRequest;
import com.gole.api.admin.application.port.in.RecordAdminActionUseCase;
import com.gole.api.admin.application.port.out.AdminReadModelPort;
import com.gole.api.admin.application.service.ResolveReportTargetService;
import com.gole.api.community.application.port.in.ModerateCommentUseCase;
import com.gole.api.community.application.port.in.ModeratePostUseCase;
import com.gole.api.listing.application.port.in.ModerateListingUseCase;
import com.gole.api.order.application.port.in.ManageSettlementsUseCase;
import com.gole.api.order.application.port.in.PayOrderUseCase;
import com.gole.api.order.domain.model.OrderStatus;
import com.gole.api.report.application.port.in.ManageReportsUseCase;
import com.gole.api.report.domain.exception.ReportAlreadyHandledException;
import com.gole.api.report.domain.model.Report;
import com.gole.api.report.domain.model.ReportReason;
import com.gole.api.report.domain.model.ReportStatus;
import com.gole.api.report.domain.model.ReportTargetType;
import com.gole.api.review.application.port.in.ModerateReviewUseCase;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AdminModerationControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");

    private final ModerateListingUseCase listings = mock(ModerateListingUseCase.class);
    private final ModeratePostUseCase posts = mock(ModeratePostUseCase.class);
    private final ModerateCommentUseCase comments = mock(ModerateCommentUseCase.class);
    private final ManageReportsUseCase reports = mock(ManageReportsUseCase.class);
    private final ModerateReviewUseCase reviews = mock(ModerateReviewUseCase.class);
    private final ManageSettlementsUseCase settlements = mock(ManageSettlementsUseCase.class);
    private final PayOrderUseCase payments = mock(PayOrderUseCase.class);
    private final RecordAdminActionUseCase audit = mock(RecordAdminActionUseCase.class);
    private final ResolveReportTargetService resolveTarget =
            new ResolveReportTargetService(reports, listings, posts, comments, reviews);
    private final AdminModerationController controller = new AdminModerationController(
            mock(AdminReadModelPort.class), listings, posts, reports, settlements, payments, audit, resolveTarget);

    @Test
    @DisplayName("결제 재조정 성공 상태와 감사 로그를 반환한다")
    void reconcilesPaymentAndAudits() {
        when(payments.pay("order-1")).thenReturn(OrderStatus.FUNDS_HELD);

        var result = controller.reconcilePayment("order-1", new MockHttpServletRequest());

        assertThat(result.status()).isEqualTo("FUNDS_HELD");
        verify(payments).pay("order-1");
        verify(audit).record(any());
    }

    @Test
    @DisplayName("신고 매물을 내리고 신고 완료와 두 감사 로그를 한 번에 남긴다")
    void resolvesListingTarget() {
        Report report = pendingReport(ReportTargetType.LISTING, "listing-1");
        when(reports.get("report-1")).thenReturn(report);
        when(reports.resolve("report-1")).thenAnswer(ignored -> {
            report.resolve(NOW.plusSeconds(1));
            return report;
        });

        var result =
                controller.resolveReportTarget("report-1", new ReasonRequest("가품 확인"), new MockHttpServletRequest());

        assertThat(result.status()).isEqualTo(ReportStatus.RESOLVED.name());
        verify(listings).takedown("listing-1", "가품 확인");
        verify(posts, never()).removeByModerator(any(), any());
        verify(reports).resolve("report-1");
        verify(audit, times(2)).record(any());
    }

    @Test
    @DisplayName("이미 처리된 신고는 대상에 손대지 않는다")
    void rejectsHandledReportBeforeTargetAction() {
        Report report = pendingReport(ReportTargetType.POST, "post-1");
        report.dismiss(NOW.plusSeconds(1));
        when(reports.get("report-1")).thenReturn(report);

        assertThatThrownBy(() -> controller.resolveReportTarget(
                        "report-1", new ReasonRequest("스팸"), new MockHttpServletRequest()))
                .isInstanceOf(ReportAlreadyHandledException.class);

        verify(listings, never()).takedown(any(), any());
        verify(posts, never()).removeByModerator(any(), any());
        verify(reports, never()).resolve(any());
        verify(audit, never()).record(any());
    }

    @Test
    @DisplayName("신고 후기를 블라인드하고 신고 완료와 감사 로그를 남긴다")
    void resolvesReviewTarget() {
        Report report = pendingReport(ReportTargetType.REVIEW, "review-1");
        when(reports.get("report-1")).thenReturn(report);
        when(reports.resolve("report-1")).thenAnswer(ignored -> {
            report.resolve(NOW.plusSeconds(1));
            return report;
        });

        var result =
                controller.resolveReportTarget("report-1", new ReasonRequest("욕설 포함"), new MockHttpServletRequest());

        assertThat(result.status()).isEqualTo(ReportStatus.RESOLVED.name());
        verify(reviews).hide("review-1", "욕설 포함");
        verify(reports).resolve("report-1");
        verify(audit, times(2)).record(any());
    }

    @Test
    @DisplayName("신고 댓글을 블라인드하고 신고 완료와 감사 로그를 남긴다")
    void resolvesCommentTarget() {
        Report report = pendingReport(ReportTargetType.COMMENT, "comment-1");
        when(reports.get("report-1")).thenReturn(report);
        when(reports.resolve("report-1")).thenAnswer(ignored -> {
            report.resolve(NOW.plusSeconds(1));
            return report;
        });

        var result =
                controller.resolveReportTarget("report-1", new ReasonRequest("욕설 포함"), new MockHttpServletRequest());

        assertThat(result.status()).isEqualTo(ReportStatus.RESOLVED.name());
        verify(comments).hide("comment-1", "욕설 포함");
        verify(reports).resolve("report-1");
        verify(audit, times(2)).record(any());
    }

    private static Report pendingReport(ReportTargetType type, String targetId) {
        return Report.submit("report-1", "reporter-1", type, targetId, ReportReason.COUNTERFEIT, "detail", NOW);
    }
}
