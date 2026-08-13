package com.gole.api.admin.adapter.in.web;

import com.gole.api.admin.adapter.in.web.AdminDtos.AuditRow;
import com.gole.api.admin.adapter.in.web.AdminDtos.OverviewResponse;
import com.gole.api.admin.application.port.in.ListAdminActionsUseCase;
import com.gole.api.admin.application.port.out.AdminReadModelPort;
import com.gole.api.admin.application.port.out.AdminReadModelPort.OrderStats;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영 대시보드 — 집계 지표와 감사 로그. (admin-console 요구사항 2, 8.3)
 *
 * <p>읽기 전용이므로 감사 대상이 아니다. {@code /api/admin/**} 의 ADMIN 강제는
 * {@link AdminAuthInterceptor}가 담당한다.
 */
@Tag(name = "Admin · 대시보드", description = "운영 지표 집계 및 관리자 조치 이력")
@RestController
@RequestMapping("/api/admin")
public class AdminDashboardController {

    private static final List<String> COLLECTIONS =
            List.of("accounts", "lego_sets", "listings", "orders", "posts", "reviews", "price_transactions");

    private final AdminReadModelPort readModel;
    private final ListAdminActionsUseCase listAdminActions;

    public AdminDashboardController(AdminReadModelPort readModel, ListAdminActionsUseCase listAdminActions) {
        this.readModel = readModel;
        this.listAdminActions = listAdminActions;
    }

    @Operation(summary = "대시보드 집계", description = "컬렉션 카운트 + GMV·플랫폼 수익·주문상태·활성매물")
    @GetMapping("/overview")
    public OverviewResponse overview() {
        OrderStats stats = readModel.orderStats();
        return new OverviewResponse(
                readModel.collectionCounts(COLLECTIONS),
                stats.completedGmv(),
                stats.platformRevenue(),
                stats.countByStatus(),
                readModel.activeListingCount());
    }

    @Operation(summary = "감사 로그", description = "관리자 조치 이력을 최근순으로 조회합니다(ADMIN 전용).")
    @GetMapping("/audit")
    public List<AuditRow> audit(@RequestParam(value = "limit", defaultValue = "50") int limit) {
        return listAdminActions.recent(limit).stream().map(AuditRow::from).toList();
    }
}
