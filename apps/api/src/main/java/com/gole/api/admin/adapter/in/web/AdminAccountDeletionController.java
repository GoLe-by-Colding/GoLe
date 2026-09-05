package com.gole.api.admin.adapter.in.web;

import com.gole.api.account.application.port.in.ManageAccountDeletionRequestsUseCase;
import com.gole.api.account.application.port.in.ManageAccountDeletionRequestsUseCase.Command;
import com.gole.api.account.application.port.in.ManageAccountDeletionRequestsUseCase.Result;
import com.gole.api.account.domain.model.AccountDeletionBlocker;
import com.gole.api.account.domain.model.AccountDeletionHoldReason;
import com.gole.api.account.domain.model.AccountDeletionStatus;
import com.gole.api.admin.application.port.in.RecordAdminActionUseCase;
import com.gole.api.admin.application.port.in.RecordAdminActionUseCase.RecordAdminActionCommand;
import com.gole.api.admin.domain.model.AdminActionType;
import com.gole.api.admin.domain.model.AdminTargetType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 전용 회원 탈퇴 보존 검토·파기 API. 응답과 감사에 탈퇴 대상 accountId/이메일을 노출하지 않는다. */
@Tag(name = "Admin · 회원 탈퇴", description = "회원 탈퇴 보존 검토 및 연계 파기")
@RestController
@RequestMapping("/api/admin/account-deletion-requests")
public class AdminAccountDeletionController {

    private final ManageAccountDeletionRequestsUseCase deletions;
    private final RecordAdminActionUseCase audit;

    public AdminAccountDeletionController(
            ManageAccountDeletionRequestsUseCase deletions, RecordAdminActionUseCase audit) {
        this.deletions = deletions;
        this.audit = audit;
    }

    @GetMapping
    public List<DeletionRow> list(
            @RequestParam(value = "status", required = false) AccountDeletionStatus status,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            HttpServletRequest http) {
        AdminActor actor = AdminActor.of(http);
        return deletions.list(status, limit, actor.id()).stream()
                .map(DeletionRow::from)
                .toList();
    }

    @Operation(summary = "보존 차단 조건 재검사")
    @PostMapping("/{requestId}/review")
    public DeletionRow review(@PathVariable String requestId, HttpServletRequest http) {
        AdminActor actor = AdminActor.of(http);
        Result result = deletions.review(requestId, actor.id());
        record(actor, AdminActionType.ACCOUNT_DELETION_REVIEW, requestId, blockerReason(result));
        return DeletionRow.from(result);
    }

    @Operation(summary = "탈퇴 파기 보존 중지 설정")
    @PostMapping("/{requestId}/hold")
    public DeletionRow placeHold(
            @PathVariable String requestId, @Valid @RequestBody HoldRequest body, HttpServletRequest http) {
        AdminActor actor = AdminActor.of(http);
        Result result = deletions.placeHold(requestId, body.confirmation(), body.reasonCode(), actor.id());
        record(
                actor,
                AdminActionType.ACCOUNT_DELETION_HOLD,
                requestId,
                body.reasonCode().name());
        return DeletionRow.from(result);
    }

    @Operation(summary = "탈퇴 파기 보존 중지 해제")
    @PostMapping("/{requestId}/hold/release")
    public DeletionRow releaseHold(
            @PathVariable String requestId, @Valid @RequestBody ConfirmationRequest body, HttpServletRequest http) {
        AdminActor actor = AdminActor.of(http);
        Result result = deletions.releaseHold(requestId, body.confirmation(), actor.id());
        record(actor, AdminActionType.ACCOUNT_DELETION_HOLD_RELEASE, requestId, null);
        return DeletionRow.from(result);
    }

    @Operation(summary = "회원 연계 개인정보 파기", description = "요청 ID 재입력과 보존 검토 확인이 필요하며, 차단 조건을 트랜잭션 안에서 다시 검사합니다.")
    @PostMapping("/{requestId}/complete")
    public DeletionRow complete(
            @PathVariable String requestId,
            @Valid @RequestBody CompletionRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest http) {
        AdminActor actor = AdminActor.of(http);
        Result result = deletions.complete(
                new Command(requestId, body.confirmation(), body.preservationReviewed(), idempotencyKey, actor.id()));
        AdminActionType type = result.status() == AccountDeletionStatus.COMPLETED
                ? AdminActionType.ACCOUNT_DELETION_COMPLETE
                : AdminActionType.ACCOUNT_DELETION_REVIEW;
        record(actor, type, requestId, blockerReason(result));
        return DeletionRow.from(result);
    }

    private void record(AdminActor actor, AdminActionType type, String requestId, String reason) {
        audit.record(new RecordAdminActionCommand(
                actor.id(), actor.email(), type, AdminTargetType.ACCOUNT_DELETION_REQUEST, requestId, reason));
    }

    private static String blockerReason(Result result) {
        return result.blockers().isEmpty()
                ? null
                : result.blockers().stream()
                        .map(Enum::name)
                        .sorted()
                        .reduce((a, b) -> a + "," + b)
                        .orElse(null);
    }

    public record HoldRequest(
            @NotBlank @Size(max = 64) String confirmation, @NotNull AccountDeletionHoldReason reasonCode) {}

    public record ConfirmationRequest(@NotBlank @Size(max = 64) String confirmation) {}

    public record CompletionRequest(@NotBlank @Size(max = 64) String confirmation, boolean preservationReviewed) {}

    public record DeletionRow(
            String requestId,
            AccountDeletionStatus status,
            List<AccountDeletionBlocker> blockers,
            AccountDeletionHoldReason holdReason,
            Instant requestedAt,
            Instant updatedAt,
            Instant completedAt,
            Map<String, Long> deletionCounts) {
        static DeletionRow from(Result result) {
            return new DeletionRow(
                    result.requestId(),
                    result.status(),
                    result.blockers(),
                    result.holdReason(),
                    result.requestedAt(),
                    result.updatedAt(),
                    result.completedAt(),
                    result.deletionCounts());
        }
    }
}
