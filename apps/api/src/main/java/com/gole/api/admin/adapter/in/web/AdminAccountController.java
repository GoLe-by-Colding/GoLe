package com.gole.api.admin.adapter.in.web;

import com.gole.api.account.application.port.in.ManageAccountsUseCase;
import com.gole.api.admin.adapter.in.web.AdminDtos.AccountRow;
import com.gole.api.admin.adapter.in.web.AdminDtos.ChangeRoleRequest;
import com.gole.api.admin.adapter.in.web.AdminDtos.ReasonRequest;
import com.gole.api.admin.application.port.in.RecordAdminActionUseCase;
import com.gole.api.admin.application.port.in.RecordAdminActionUseCase.RecordAdminActionCommand;
import com.gole.api.admin.domain.model.AdminActionType;
import com.gole.api.admin.domain.model.AdminTargetType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 관리 — 조회, 정지/해제, 권한 변경. (admin-console 요구사항 6)
 *
 * <p>정지에 따른 세션 폐기, 자기 자신·마지막 관리자 가드는 모두
 * {@link ManageAccountsUseCase} 구현이 책임진다. 웹 계층은 조치자 식별과 감사만 더한다.
 */
@Tag(name = "Admin · 회원", description = "회원 조회 및 정지·권한 관리")
@RestController
@RequestMapping("/api/admin/accounts")
public class AdminAccountController {

    private final ManageAccountsUseCase manageAccounts;
    private final RecordAdminActionUseCase audit;

    public AdminAccountController(ManageAccountsUseCase manageAccounts, RecordAdminActionUseCase audit) {
        this.manageAccounts = manageAccounts;
        this.audit = audit;
    }

    @Operation(summary = "회원 목록", description = "이메일 부분 일치 검색과 건수 제한을 적용해 최근 가입순으로 조회합니다.")
    @GetMapping
    public List<AccountRow> accounts(
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "limit", defaultValue = "30") int limit) {
        return manageAccounts.list(query, limit).stream().map(AccountRow::from).toList();
    }

    @Operation(summary = "회원 정지", description = "사유를 받아 계정을 정지하고 활성 세션을 즉시 폐기합니다. 자기 자신·마지막 관리자는 거부됩니다.")
    @PostMapping("/{accountId}/suspend")
    public AccountRow suspend(
            @PathVariable String accountId, @Valid @RequestBody ReasonRequest request, HttpServletRequest http) {
        AdminActor actor = AdminActor.of(http);
        AccountRow row = AccountRow.from(manageAccounts.suspend(accountId, actor.id(), request.reason()));
        record(actor, AdminActionType.ACCOUNT_SUSPEND, accountId, request.reason());
        return row;
    }

    @Operation(summary = "회원 정지 해제", description = "계정을 복구하고 로그인 실패 카운터·잠금을 초기화합니다.")
    @PostMapping("/{accountId}/reinstate")
    public AccountRow reinstate(@PathVariable String accountId, HttpServletRequest http) {
        AdminActor actor = AdminActor.of(http);
        AccountRow row = AccountRow.from(manageAccounts.reinstate(accountId, actor.id()));
        record(actor, AdminActionType.ACCOUNT_REINSTATE, accountId, null);
        return row;
    }

    @Operation(summary = "회원 권한 변경", description = "role 을 변경하고 세션을 폐기해 새 권한으로 재로그인하게 합니다.")
    @PostMapping("/{accountId}/role")
    public AccountRow changeRole(
            @PathVariable String accountId, @Valid @RequestBody ChangeRoleRequest request, HttpServletRequest http) {
        AdminActor actor = AdminActor.of(http);
        AccountRow row = AccountRow.from(manageAccounts.changeRole(accountId, actor.id(), request.role()));
        record(
                actor,
                AdminActionType.ACCOUNT_ROLE_CHANGE,
                accountId,
                request.role().name());
        return row;
    }

    private void record(AdminActor actor, AdminActionType type, String accountId, String reason) {
        audit.record(new RecordAdminActionCommand(
                actor.id(), actor.email(), type, AdminTargetType.ACCOUNT, accountId, reason));
    }
}
