package com.gole.api.admin.adapter.in.web;

import com.gole.api.account.application.port.in.ManageAccountsUseCase.AccountSummary;
import com.gole.api.account.domain.model.Role;
import com.gole.api.admin.application.port.out.AdminReadModelPort;
import com.gole.api.admin.domain.model.AdminAction;
import com.gole.api.catalog.application.port.in.ListLegoSetsUseCase.LegoSetSummary;
import com.gole.api.catalog.domain.model.LegoSet;
import com.gole.api.catalog.domain.model.RetirementStatus;
import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase.ConfigurationIssue;
import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase.Snapshot;
import com.gole.api.order.application.port.in.ManageSettlementsUseCase.SettlementSummary;
import com.gole.api.report.domain.model.Report;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 관리자 API의 요청/응답 DTO.
 *
 * <p>응답 record는 읽기 모델·유스케이스 결과를 웹 표현으로 옮기기만 한다.
 * 저장소 도큐먼트를 직접 다루지 않는다(그 책임은 {@link AdminReadModelPort} 어댑터에 있다).
 */
public final class AdminDtos {

    private static final int CONTENT_PREVIEW_LENGTH = 80;

    private AdminDtos() {}

    // ── 대시보드 ──────────────────────────────────────────────

    public record OverviewResponse(
            Map<String, Long> counts,
            long gmv,
            Map<String, Long> ordersByStatus,
            long activeListings,
            long pendingReports,
            long pendingSettlements,
            PaymentReadinessResponse paymentReadiness) {}

    /** 설정값 원문 없이 결제 연동의 운영 준비 상태만 노출한다. */
    public record PaymentReadinessResponse(
            boolean enabled,
            boolean ready,
            String state,
            String channelType,
            String provider,
            String currency,
            List<PaymentConfigurationIssueResponse> issues) {

        public static PaymentReadinessResponse from(Snapshot snapshot) {
            return new PaymentReadinessResponse(
                    snapshot.enabled(),
                    snapshot.ready(),
                    snapshot.state().name(),
                    snapshot.channelType().name(),
                    snapshot.provider(),
                    snapshot.currency(),
                    snapshot.issues().stream()
                            .map(PaymentConfigurationIssueResponse::from)
                            .toList());
        }
    }

    public record PaymentConfigurationIssueResponse(String setting, String problem) {

        private static PaymentConfigurationIssueResponse from(ConfigurationIssue issue) {
            return new PaymentConfigurationIssueResponse(
                    issue.setting(), issue.problem().name());
        }
    }

    // ── 모니터링 행 ────────────────────────────────────────────

    public record OrderRow(
            String id,
            String status,
            long amount,
            String buyerId,
            String sellerId,
            String catalogSetNumber,
            Instant createdAt) {

        public static OrderRow from(AdminReadModelPort.OrderRow row) {
            return new OrderRow(
                    row.id(),
                    row.status(),
                    row.amount(),
                    row.buyerId(),
                    row.sellerId(),
                    row.catalogSetNumber(),
                    row.createdAt());
        }
    }

    public record ListingRow(
            String id, String title, String sellerId, long price, String status, String category, Instant createdAt) {

        public static ListingRow from(AdminReadModelPort.ListingRow row) {
            return new ListingRow(
                    row.id(), row.title(), row.sellerId(), row.price(), row.status(), row.category(), row.createdAt());
        }
    }

    public record PostRow(String id, String authorId, String content, String type, String status, Instant createdAt) {

        public static PostRow from(AdminReadModelPort.PostRow row) {
            return new PostRow(
                    row.id(), row.authorId(), preview(row.content()), row.type(), row.status(), row.createdAt());
        }

        private static String preview(String content) {
            if (content == null) {
                return "";
            }
            return content.length() > CONTENT_PREVIEW_LENGTH
                    ? content.substring(0, CONTENT_PREVIEW_LENGTH) + "…"
                    : content;
        }
    }

    // ── 회원 ──────────────────────────────────────────────────

    public record AccountRow(
            String id, String email, String role, String status, Instant lockedUntil, String suspendedReason) {

        public static AccountRow from(AccountSummary summary) {
            return new AccountRow(
                    summary.id(),
                    summary.email(),
                    summary.role().name(),
                    summary.status().name(),
                    summary.lockedUntil(),
                    summary.suspendedReason());
        }
    }

    // ── 조치 요청 ──────────────────────────────────────────────

    /** 사유가 필수인 모더레이션 조치(매물 내림·게시글 삭제·계정 정지). */
    public record ReasonRequest(@NotBlank(message = "조치 사유를 입력해야 합니다") String reason) {}

    public record ChangeRoleRequest(@NotNull Role role) {}

    public record FeaturedRequest(boolean featured) {}

    public record MarkSettlementPaidRequest(
            @NotBlank(message = "지급 증빙 번호를 입력해야 합니다") @Size(max = 120) String paymentReference) {}

    public record SettlementRow(
            String orderId,
            String sellerId,
            long grossAmount,
            long fee,
            long payout,
            double feeRate,
            String status,
            String paymentReference,
            Instant createdAt,
            Instant paidAt) {

        public static SettlementRow from(SettlementSummary summary) {
            return new SettlementRow(
                    summary.orderId(),
                    summary.sellerId(),
                    summary.grossAmount(),
                    summary.fee(),
                    summary.payout(),
                    summary.feeRate(),
                    summary.status().name(),
                    summary.paymentReference(),
                    summary.createdAt(),
                    summary.paidAt());
        }
    }

    public record PaymentReconciliationResponse(String orderId, String status) {}

    // ── 카탈로그 ───────────────────────────────────────────────

    public record CreateSetRequest(
            @NotBlank String setNumber,
            @NotBlank String name,
            @NotBlank String theme,
            @Min(0) int pieceCount,
            int releaseYear,
            @NotNull RetirementStatus retirementStatus,
            String imageUrl,
            boolean featured) {}

    public record UpdateSetRequest(
            @NotBlank String name,
            @NotBlank String theme,
            @Min(0) int pieceCount,
            int releaseYear,
            @NotNull RetirementStatus retirementStatus,
            String imageUrl,
            boolean featured) {}

    public record LegoSetResponse(
            String setNumber,
            String name,
            String theme,
            int pieceCount,
            int releaseYear,
            String retirementStatus,
            String imageUrl,
            boolean featured) {

        public static LegoSetResponse from(LegoSetSummary summary) {
            return from(summary.set(), summary.featured());
        }

        public static LegoSetResponse from(LegoSet s, boolean featured) {
            return new LegoSetResponse(
                    s.getSetNumber(),
                    s.getName(),
                    s.getTheme(),
                    s.getPieceCount(),
                    s.getReleaseYear(),
                    s.getRetirementStatus().name(),
                    s.getImageUrl(),
                    featured);
        }
    }

    // ── 신고 ──────────────────────────────────────────────────

    /** 신고 큐 행 — notice & takedown 모더레이션용. */
    public record ReportRow(
            String id,
            String reporterId,
            String targetType,
            String targetId,
            String reason,
            String detail,
            String status,
            Instant createdAt,
            Instant handledAt) {

        public static ReportRow from(Report r) {
            return new ReportRow(
                    r.getId(),
                    r.getReporterId(),
                    r.getTargetType().name(),
                    r.getTargetId(),
                    r.getReason().name(),
                    r.getDetail(),
                    r.getStatus().name(),
                    r.getCreatedAt(),
                    r.getHandledAt());
        }
    }

    // ── 감사 로그 ──────────────────────────────────────────────

    public record AuditRow(
            String id,
            String actorId,
            String actorEmail,
            String type,
            String targetType,
            String targetId,
            String reason,
            Instant occurredAt) {

        public static AuditRow from(AdminAction action) {
            return new AuditRow(
                    action.getId(),
                    action.getActorId(),
                    action.getActorEmail(),
                    action.getType().name(),
                    action.getTargetType().name(),
                    action.getTargetId(),
                    action.getReason(),
                    action.getOccurredAt());
        }
    }
}
