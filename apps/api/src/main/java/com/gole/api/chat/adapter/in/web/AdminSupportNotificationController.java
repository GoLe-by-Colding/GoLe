package com.gole.api.chat.adapter.in.web;

import com.gole.api.admin.adapter.in.web.AdminActor;
import com.gole.api.admin.application.port.in.RecordAdminActionUseCase;
import com.gole.api.admin.application.port.in.RecordAdminActionUseCase.RecordAdminActionCommand;
import com.gole.api.admin.domain.model.AdminActionType;
import com.gole.api.admin.domain.model.AdminTargetType;
import com.gole.api.chat.application.SupportNotificationOutboxAdminService;
import com.gole.api.chat.application.SupportNotificationOutboxAdminService.RequeueReasonCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 운영자 전용 비식별 문의 Discord dead-letter 복구 경로. */
@Tag(name = "Admin · Support notifications", description = "문의 Discord 알림 dead-letter 복구")
@Validated
@RestController
@RequestMapping("/api/admin/support-notifications")
public class AdminSupportNotificationController {

    private static final String EVENT_ID_PATTERN =
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}";

    private final SupportNotificationOutboxAdminService notifications;
    private final RecordAdminActionUseCase audit;

    public AdminSupportNotificationController(
            SupportNotificationOutboxAdminService notifications, RecordAdminActionUseCase audit) {
        this.notifications = notifications;
        this.audit = audit;
    }

    @Operation(
            summary = "문의 Discord dead-letter 재큐잉",
            description = "Discord 설정·장애 복구를 확인한 뒤 정확한 이벤트 ID 확인 문구와 정형 사유로 한 건만 재큐잉합니다.")
    @PostMapping("/{eventId}/requeue")
    @Transactional
    public RequeueResponse requeue(
            @PathVariable @Pattern(regexp = EVENT_ID_PATTERN) String eventId,
            @Valid @RequestBody RequeueRequest request,
            HttpServletRequest http) {
        AdminActor actor = AdminActor.of(http);
        var outcome = notifications.requeue(eventId, request.confirmation(), request.reasonCode());
        if (outcome.changed()) {
            audit.record(new RecordAdminActionCommand(
                    actor.id(),
                    actor.email(),
                    AdminActionType.SUPPORT_NOTIFICATION_REQUEUE,
                    AdminTargetType.SUPPORT_NOTIFICATION,
                    outcome.event().eventId(),
                    "reasonCode=" + request.reasonCode().name()));
        }
        return RequeueResponse.from(outcome);
    }

    public record RequeueRequest(
            @NotBlank @Size(max = 80) String confirmation, @NotNull RequeueReasonCode reasonCode) {}

    public record RequeueResponse(String eventId, String state, int attempts, String nextAttemptAt, boolean changed) {

        static RequeueResponse from(SupportNotificationOutboxAdminService.RequeueOutcome outcome) {
            var event = outcome.event();
            return new RequeueResponse(
                    event.eventId(),
                    event.state().name(),
                    event.attempts(),
                    event.nextAttemptAt() == null ? null : event.nextAttemptAt().toString(),
                    outcome.changed());
        }
    }
}
