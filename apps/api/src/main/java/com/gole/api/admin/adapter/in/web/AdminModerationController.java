package com.gole.api.admin.adapter.in.web;

import com.gole.api.admin.adapter.in.web.AdminDtos.ListingRow;
import com.gole.api.admin.adapter.in.web.AdminDtos.MarkSettlementPaidRequest;
import com.gole.api.admin.adapter.in.web.AdminDtos.OrderRow;
import com.gole.api.admin.adapter.in.web.AdminDtos.PaymentReconciliationResponse;
import com.gole.api.admin.adapter.in.web.AdminDtos.PostRow;
import com.gole.api.admin.adapter.in.web.AdminDtos.ReasonRequest;
import com.gole.api.admin.adapter.in.web.AdminDtos.RecoverSettlementRequest;
import com.gole.api.admin.adapter.in.web.AdminDtos.ReportRow;
import com.gole.api.admin.adapter.in.web.AdminDtos.SettlementRow;
import com.gole.api.admin.application.port.in.RecordAdminActionUseCase;
import com.gole.api.admin.application.port.in.RecordAdminActionUseCase.RecordAdminActionCommand;
import com.gole.api.admin.application.port.out.AdminReadModelPort;
import com.gole.api.admin.application.service.ResolveReportTargetService;
import com.gole.api.admin.domain.model.AdminActionType;
import com.gole.api.admin.domain.model.AdminTargetType;
import com.gole.api.community.application.port.in.ModeratePostUseCase;
import com.gole.api.listing.application.port.in.ModerateListingUseCase;
import com.gole.api.order.application.port.in.ManageSettlementsUseCase;
import com.gole.api.order.application.port.in.ManageSettlementsUseCase.SettlementStatus;
import com.gole.api.order.application.port.in.PayOrderUseCase;
import com.gole.api.report.application.port.in.ManageReportsUseCase;
import com.gole.api.report.domain.model.ReportStatus;
import com.gole.api.report.domain.model.ReportTargetType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 콘텐츠 모더레이션 — 신고 큐, 매물 내림, 게시글 삭제, 주문 모니터링.
 * (admin-console 요구사항 3, 4, 5, 7.1)
 *
 * <p>조회는 읽기 모델을, 상태 변경은 각 컨텍스트의 인바운드 포트를 사용한다(요구사항 9.1).
 * 성공한 조치는 예외 없이 감사 로그로 남긴다(요구사항 8.1, 8.2 — 실패 시엔 기록하지 않으므로
 * 기록 호출은 항상 유스케이스 호출 <b>뒤</b>에 온다).
 */
@Tag(name = "Admin · 모더레이션", description = "신고 처리, 매물·게시글 내림, 주문 모니터링")
@RestController
@RequestMapping("/api/admin")
public class AdminModerationController {

    private static final int MAX_ROWS = 100;

    private final AdminReadModelPort readModel;
    private final ModerateListingUseCase moderateListing;
    private final ModeratePostUseCase moderatePost;
    private final ManageReportsUseCase manageReports;
    private final ManageSettlementsUseCase manageSettlements;
    private final PayOrderUseCase payOrders;
    private final RecordAdminActionUseCase audit;
    private final ResolveReportTargetService resolveReportTarget;

    public AdminModerationController(
            AdminReadModelPort readModel,
            ModerateListingUseCase moderateListing,
            ModeratePostUseCase moderatePost,
            ManageReportsUseCase manageReports,
            ManageSettlementsUseCase manageSettlements,
            PayOrderUseCase payOrders,
            RecordAdminActionUseCase audit,
            ResolveReportTargetService resolveReportTarget) {
        this.readModel = readModel;
        this.moderateListing = moderateListing;
        this.moderatePost = moderatePost;
        this.manageReports = manageReports;
        this.manageSettlements = manageSettlements;
        this.payOrders = payOrders;
        this.audit = audit;
        this.resolveReportTarget = resolveReportTarget;
    }

    // ── 주문 (읽기 전용) ────────────────────────────────────────

    @Operation(summary = "주문 모니터링", description = "상태 필터를 적용해 최근 주문을 조회합니다.")
    @GetMapping("/orders")
    public List<OrderRow> orders(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "limit", defaultValue = "30") int limit) {
        return readModel.recentOrders(status, query, clamp(limit)).stream()
                .map(OrderRow::from)
                .toList();
    }

    @Operation(summary = "결제 상태 재조정", description = "PortOne 원장을 다시 조회해 결제 대기 주문을 승인·실패·대기 상태로 안전하게 재조정합니다.")
    @PostMapping("/orders/{orderId}/reconcile-payment")
    public PaymentReconciliationResponse reconcilePayment(@PathVariable String orderId, HttpServletRequest http) {
        String status = payOrders.pay(orderId).name();
        record(http, AdminActionType.ORDER_PAYMENT_RECONCILE, AdminTargetType.ORDER, orderId, status);
        return new PaymentReconciliationResponse(orderId, status);
    }

    @Operation(summary = "정산 원장", description = "판매자 정산 대기·지급 완료 내역을 조회합니다.")
    @GetMapping("/settlements")
    public List<SettlementRow> settlements(
            @RequestParam(value = "status", required = false) SettlementStatus status,
            @RequestParam(value = "limit", defaultValue = "30") int limit) {
        return manageSettlements.list(status, clamp(limit)).stream()
                .map(SettlementRow::from)
                .toList();
    }

    @Operation(summary = "정산 지급 완료", description = "외부 송금 증빙 번호와 함께 정산을 지급 완료 처리합니다.")
    @PostMapping("/settlements/{orderId}/paid")
    public SettlementRow markSettlementPaid(
            @PathVariable String orderId,
            @Valid @RequestBody MarkSettlementPaidRequest request,
            HttpServletRequest http) {
        AdminActor actor = AdminActor.of(http);
        SettlementRow row =
                SettlementRow.from(manageSettlements.markPaid(orderId, actor.id(), request.paymentReference()));
        record(
                http,
                AdminActionType.SETTLEMENT_MARK_PAID,
                AdminTargetType.SETTLEMENT,
                orderId,
                request.paymentReference());
        return row;
    }

    @Operation(summary = "수동 정산 작업 배정", description = "외부 이체 전에 원장을 현재 운영자에게 원자적으로 배정합니다.")
    @PostMapping("/settlements/{orderId}/claim")
    public SettlementRow claimSettlement(@PathVariable String orderId, HttpServletRequest http) {
        AdminActor actor = AdminActor.of(http);
        SettlementRow row = SettlementRow.from(manageSettlements.claimManualPayout(orderId, actor.id()));
        record(http, AdminActionType.SETTLEMENT_CLAIM, AdminTargetType.SETTLEMENT, orderId, null);
        return row;
    }

    @Operation(summary = "수동 정산 재조정", description = "진행 중 원장을 잠그고 외부 지급 결과를 반드시 확인해야 하는 상태로 전환합니다.")
    @PostMapping("/settlements/{orderId}/reconcile")
    public SettlementRow reconcileSettlement(
            @PathVariable String orderId, @Valid @RequestBody ReasonRequest request, HttpServletRequest http) {
        AdminActor actor = AdminActor.of(http);
        SettlementRow row =
                SettlementRow.from(manageSettlements.reconcileManualPayout(orderId, actor.id(), request.reason()));
        record(http, AdminActionType.SETTLEMENT_RECONCILE, AdminTargetType.SETTLEMENT, orderId, request.reason());
        return row;
    }

    @Operation(summary = "차단 정산 결과 복구", description = "외부 지급 결과를 확인해 지급 완료로 기록하거나 미지급 작업을 현재 운영자에게 다시 배정합니다.")
    @PostMapping("/settlements/{orderId}/recover")
    public SettlementRow recoverSettlement(
            @PathVariable String orderId,
            @Valid @RequestBody RecoverSettlementRequest request,
            HttpServletRequest http) {
        AdminActor actor = AdminActor.of(http);
        SettlementRow row = SettlementRow.from(manageSettlements.recoverBlockedPayout(
                orderId, actor.id(), request.alreadyPaid(), request.paymentReference(), request.reason()));
        record(
                http,
                AdminActionType.SETTLEMENT_RECOVER,
                AdminTargetType.SETTLEMENT,
                orderId,
                (request.alreadyPaid() ? "지급 확인 · " : "미지급 확인 · ") + request.reason());
        return row;
    }

    // ── 매물 ──────────────────────────────────────────────────

    @Operation(summary = "매물 목록", description = "DELETED 를 포함한 전체 상태의 최근 매물을 조회합니다.")
    @GetMapping("/listings")
    public List<ListingRow> listings(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "limit", defaultValue = "30") int limit) {
        return readModel.recentListings(status, query, clamp(limit)).stream()
                .map(ListingRow::from)
                .toList();
    }

    @Operation(summary = "매물 강제 내림", description = "사유를 받아 매물을 내립니다. 진행 중 주문이 있어도 내려갑니다(모더레이션 우선).")
    @PostMapping("/listings/{listingId}/takedown")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void takedownListing(
            @PathVariable String listingId, @Valid @RequestBody ReasonRequest request, HttpServletRequest http) {
        moderateListing.takedown(listingId, request.reason());
        record(http, AdminActionType.LISTING_TAKEDOWN, AdminTargetType.LISTING, listingId, request.reason());
    }

    // ── 커뮤니티 ───────────────────────────────────────────────

    @Operation(summary = "게시글 목록", description = "전체 상태의 최근 게시글을 조회합니다.")
    @GetMapping("/posts")
    public List<PostRow> posts(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "limit", defaultValue = "30") int limit) {
        return readModel.recentPosts(status, query, clamp(limit)).stream()
                .map(PostRow::from)
                .toList();
    }

    @Operation(summary = "게시글 강제 삭제", description = "작성자 확인 없이 게시글을 내립니다(운영자 오버라이드).")
    @PostMapping("/posts/{postId}/remove")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removePost(
            @PathVariable String postId, @Valid @RequestBody ReasonRequest request, HttpServletRequest http) {
        moderatePost.removeByModerator(postId, request.reason());
        record(http, AdminActionType.POST_REMOVE, AdminTargetType.POST, postId, request.reason());
    }

    // ── 신고 큐 ────────────────────────────────────────────────

    @Operation(summary = "신고 큐", description = "상태 필터를 적용해 신고를 조회합니다. 미지정 시 전체.")
    @GetMapping("/reports")
    public List<ReportRow> reports(
            @RequestParam(value = "status", required = false) ReportStatus status,
            @RequestParam(value = "limit", defaultValue = "30") int limit) {
        return manageReports.list(status, clamp(limit)).stream()
                .map(ReportRow::from)
                .toList();
    }

    @Operation(summary = "신고 조치 완료", description = "신고를 RESOLVED 로 전이합니다.")
    @PostMapping("/reports/{reportId}/resolve")
    public ReportRow resolveReport(@PathVariable String reportId, HttpServletRequest http) {
        ReportRow row = ReportRow.from(manageReports.resolve(reportId));
        record(http, AdminActionType.REPORT_RESOLVE, AdminTargetType.REPORT, reportId, null);
        return row;
    }

    @Operation(
            summary = "신고 대상 조치 후 완료",
            description = "매물 내림·게시글 삭제·댓글/후기 블라인드 전용입니다. 채팅 신고는 스냅샷 검토 후 별도 계정 조치를 사용합니다.")
    @PostMapping("/reports/{reportId}/resolve-target")
    public ReportRow resolveReportTarget(
            @PathVariable String reportId, @Valid @RequestBody ReasonRequest request, HttpServletRequest http) {
        var report = resolveReportTarget.resolve(reportId, request.reason());
        if (report.getTargetType() == ReportTargetType.LISTING) {
            record(
                    http,
                    AdminActionType.LISTING_TAKEDOWN,
                    AdminTargetType.LISTING,
                    report.getTargetId(),
                    request.reason());
        } else if (report.getTargetType() == ReportTargetType.POST) {
            record(http, AdminActionType.POST_REMOVE, AdminTargetType.POST, report.getTargetId(), request.reason());
        } else if (report.getTargetType() == ReportTargetType.COMMENT) {
            record(http, AdminActionType.COMMENT_HIDE, AdminTargetType.COMMENT, report.getTargetId(), request.reason());
        } else if (report.getTargetType() == ReportTargetType.REVIEW) {
            record(http, AdminActionType.REVIEW_HIDE, AdminTargetType.REVIEW, report.getTargetId(), request.reason());
        }
        ReportRow row = ReportRow.from(report);
        record(http, AdminActionType.REPORT_RESOLVE, AdminTargetType.REPORT, reportId, request.reason());
        return row;
    }

    @Operation(summary = "신고 기각", description = "신고를 DISMISSED 로 전이합니다.")
    @PostMapping("/reports/{reportId}/dismiss")
    public ReportRow dismissReport(@PathVariable String reportId, HttpServletRequest http) {
        ReportRow row = ReportRow.from(manageReports.dismiss(reportId));
        record(http, AdminActionType.REPORT_DISMISS, AdminTargetType.REPORT, reportId, null);
        return row;
    }

    @Operation(summary = "수수료 집계", description = "정산 원장 기준 수수료 총액·건수(R5.6). status 미지정 시 전체.")
    @GetMapping("/settlements/summary")
    public ManageSettlementsUseCase.FeeTotals settlementTotals(
            @RequestParam(required = false) ManageSettlementsUseCase.SettlementStatus status) {
        return manageSettlements.totals(status);
    }

    private void record(
            HttpServletRequest http, AdminActionType type, AdminTargetType targetType, String targetId, String reason) {
        AdminActor actor = AdminActor.of(http);
        audit.record(new RecordAdminActionCommand(actor.id(), actor.email(), type, targetType, targetId, reason));
    }

    private static int clamp(int limit) {
        return Math.max(1, Math.min(limit, MAX_ROWS));
    }
}
