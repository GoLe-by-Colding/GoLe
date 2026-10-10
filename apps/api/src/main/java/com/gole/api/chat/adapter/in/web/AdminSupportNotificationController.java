package com.gole.api.chat.adapter.in.web;

import com.gole.api.chat.application.port.in.RequeueSupportNotificationUseCase;
import com.gole.api.chat.application.port.in.RequeueSupportNotificationUseCase.RequeueReasonCode;
import com.gole.api.chat.domain.model.SupportOperator;
import com.gole.api.common.web.auth.AdminActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

    private final RequeueSupportNotificationUseCase notifications;

    public AdminSupportNotificationController(RequeueSupportNotificationUseCase notifications) {
        this.notifications = notifications;
    }

    @Operation(
            summary = "문의 Discord dead-letter 재큐잉",
            description = "Discord 설정·장애 복구를 확인한 뒤 정확한 이벤트 ID 확인 문구와 정형 사유로 한 건만 재큐잉합니다.")
    @PostMapping("/{eventId}/requeue")
    public RequeueResponse requeue(
            @PathVariable @Pattern(regexp = EVENT_ID_PATTERN) String eventId,
            @Valid @RequestBody RequeueRequest request,
            HttpServletRequest http) {
        AdminActor actor = AdminActor.of(http);
        var outcome = notifications.requeue(
                eventId, request.confirmation(), request.reasonCode(), new SupportOperator(actor.id(), actor.email()));
        return RequeueResponse.from(outcome);
    }

    public record RequeueRequest(
            @NotBlank @Size(max = 80) String confirmation,
            @NotNull RequeueReasonCode reasonCode) {}

    public record RequeueResponse(String eventId, String state, int attempts, String nextAttemptAt, boolean changed) {

        static RequeueResponse from(RequeueSupportNotificationUseCase.RequeueOutcome outcome) {
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
