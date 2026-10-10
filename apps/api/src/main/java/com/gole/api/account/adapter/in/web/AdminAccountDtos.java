package com.gole.api.account.adapter.in.web;

import com.gole.api.account.application.port.in.ManageAccountsUseCase.AccountSummary;
import com.gole.api.account.domain.model.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** 관리자 회원 관리 API({@code /api/admin/accounts})의 요청/응답 DTO. */
public final class AdminAccountDtos {

    private AdminAccountDtos() {}

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

    public record ChangeRoleRequest(@NotNull Role role) {}

    /** 사유가 필수인 모더레이션 조치(계정 정지). */
    public record ReasonRequest(
            @NotBlank(message = "조치 사유를 입력해야 합니다") String reason) {}
}
