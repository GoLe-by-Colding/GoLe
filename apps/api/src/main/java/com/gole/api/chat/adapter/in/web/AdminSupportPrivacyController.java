package com.gole.api.chat.adapter.in.web;

import com.gole.api.admin.adapter.in.web.AdminActor;
import com.gole.api.admin.application.port.in.RecordAdminActionUseCase;
import com.gole.api.admin.application.port.in.RecordAdminActionUseCase.RecordAdminActionCommand;
import com.gole.api.admin.domain.model.AdminActionType;
import com.gole.api.admin.domain.model.AdminTargetType;
import com.gole.api.chat.application.SupportConversationPrivacyService;
import com.gole.api.chat.application.SupportConversationPrivacyService.PurgeReasonCode;
import com.gole.api.chat.application.SupportConversationPrivacyService.RetentionHoldReasonCode;
import com.gole.api.chat.application.SupportConversationPrivacyService.RetentionReleaseReasonCode;
import com.gole.api.chat.application.port.out.SupportConversationPrivacyRepositoryPort.PurgeCounts;
import com.gole.api.chat.application.port.out.SupportConversationPrivacyRepositoryPort.PurgeReceipt;
import com.gole.api.chat.application.port.out.SupportConversationPrivacyRepositoryPort.RetentionHold;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 운영자 전용 문의 보존 중지·연계 파기 경로. 자동 삭제 API는 제공하지 않는다. */
@Tag(name = "Admin · Support privacy", description = "문의 대화 보존 중지와 연계 파기")
@Validated
@RestController
@RequestMapping("/api/admin/support-privacy")
public class AdminSupportPrivacyController {

    private final SupportConversationPrivacyService privacy;
    private final RecordAdminActionUseCase audit;

    public AdminSupportPrivacyController(SupportConversationPrivacyService privacy, RecordAdminActionUseCase audit) {
        this.privacy = privacy;
        this.audit = audit;
    }

    @Operation(
            summary = "문의 대화 연계 파기",
            description = "완료 상태, 보존 검토, 정확한 방 ID 확인과 멱등 키를 모두 검증한 뒤 관련 원문을 한 트랜잭션에서 파기합니다.")
    @PostMapping("/{roomId}/purge")
    public PurgeResponse purge(
            @PathVariable String roomId,
            @RequestHeader("Idempotency-Key")
                    @NotBlank
                    @Pattern(
                            regexp =
                                    "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}",
                            message = "멱등 키는 무작위 UUID 형식이어야 합니다")
                    String idempotencyKey,
            @Valid @RequestBody PurgeRequest request,
            HttpServletRequest http) {
        AdminActor actor = AdminActor.of(http);
        var outcome = privacy.purge(
                roomId,
                actor.id(),
                request.confirmation(),
                request.reasonCode(),
                request.preservationReviewed(),
                idempotencyKey);
        if (!outcome.replayed()) {
            PurgeCounts counts = outcome.receipt().counts();
            audit.record(new RecordAdminActionCommand(
                    actor.id(),
                    actor.email(),
                    AdminActionType.SUPPORT_CONVERSATION_PURGE,
                    AdminTargetType.SUPPORT_TICKET,
                    outcome.receipt().receiptId(),
                    "reasonCode=%s; messages=%d; tickets=%d; rooms=%d; analyses=%d; notes=%d; cursors=%d; auditRefs=%d"
                            .formatted(
                                    request.reasonCode(),
                                    counts.messages(),
                                    counts.supportTickets(),
                                    counts.socialRooms(),
                                    counts.assistantAnalyses(),
                                    counts.internalNotes(),
                                    counts.readCursors(),
                                    counts.auditReferencesAnonymized())));
        }
        return PurgeResponse.from(outcome.receipt(), outcome.replayed());
    }

    @Operation(summary = "문의 대화 보존 중지", description = "거래·분쟁·법정 의무가 있으면 파기보다 우선하는 보존 중지를 설정합니다.")
    @PostMapping("/{roomId}/retention-hold")
    public RetentionHoldResponse placeRetentionHold(
            @PathVariable String roomId, @Valid @RequestBody RetentionHoldRequest request, HttpServletRequest http) {
        AdminActor actor = AdminActor.of(http);
        var outcome = privacy.placeRetentionHold(roomId, actor.id(), request.confirmation(), request.reasonCode());
        if (outcome.changed()) {
            record(
                    actor,
                    AdminActionType.SUPPORT_RETENTION_HOLD,
                    outcome.hold().holdReference(),
                    "reasonCode=" + request.reasonCode().name());
        }
        return RetentionHoldResponse.from(outcome.hold(), outcome.changed());
    }

    @Operation(summary = "문의 대화 보존 중지 해제", description = "정형화된 해제 근거와 정확한 방 ID 확인을 남깁니다.")
    @PostMapping("/{roomId}/retention-hold/release")
    public RetentionHoldResponse releaseRetentionHold(
            @PathVariable String roomId, @Valid @RequestBody RetentionReleaseRequest request, HttpServletRequest http) {
        AdminActor actor = AdminActor.of(http);
        var outcome = privacy.releaseRetentionHold(roomId, actor.id(), request.confirmation(), request.reasonCode());
        if (outcome.changed()) {
            record(
                    actor,
                    AdminActionType.SUPPORT_RETENTION_RELEASE,
                    outcome.hold().holdReference(),
                    "reasonCode=" + request.reasonCode().name());
        }
        return RetentionHoldResponse.from(outcome.hold(), outcome.changed());
    }

    private void record(AdminActor actor, AdminActionType type, String targetReference, String reason) {
        audit.record(new RecordAdminActionCommand(
                actor.id(), actor.email(), type, AdminTargetType.SUPPORT_TICKET, targetReference, reason));
    }

    public record PurgeRequest(
            @NotBlank @Size(max = 200) String confirmation,
            @NotNull PurgeReasonCode reasonCode,
            @AssertTrue(message = "거래·분쟁·법정 보존 필요성을 검토해야 합니다") boolean preservationReviewed) {}

    public record RetentionHoldRequest(
            @NotBlank @Size(max = 200) String confirmation, @NotNull RetentionHoldReasonCode reasonCode) {}

    public record RetentionReleaseRequest(
            @NotBlank @Size(max = 200) String confirmation, @NotNull RetentionReleaseReasonCode reasonCode) {}

    public record PurgeResponse(
            String receiptId,
            String actorId,
            String reasonCode,
            String resolvedAt,
            String purgedAt,
            PurgeCountsResponse counts,
            boolean replayed) {

        static PurgeResponse from(PurgeReceipt receipt, boolean replayed) {
            return new PurgeResponse(
                    receipt.receiptId(),
                    receipt.actorId(),
                    receipt.reasonCode(),
                    receipt.resolvedAt().toString(),
                    receipt.purgedAt().toString(),
                    PurgeCountsResponse.from(receipt.counts()),
                    replayed);
        }
    }

    public record PurgeCountsResponse(
            long messages,
            long supportTickets,
            long socialRooms,
            long assistantAnalyses,
            long internalNotes,
            long readCursors,
            long retentionHolds,
            long auditReferencesAnonymized) {

        static PurgeCountsResponse from(PurgeCounts counts) {
            return new PurgeCountsResponse(
                    counts.messages(),
                    counts.supportTickets(),
                    counts.socialRooms(),
                    counts.assistantAnalyses(),
                    counts.internalNotes(),
                    counts.readCursors(),
                    counts.retentionHolds(),
                    counts.auditReferencesAnonymized());
        }
    }

    public record RetentionHoldResponse(
            String holdReference,
            boolean active,
            String reasonCode,
            String placedBy,
            String placedAt,
            String releasedBy,
            String releasedAt,
            String releaseReasonCode,
            boolean changed) {

        static RetentionHoldResponse from(RetentionHold hold, boolean changed) {
            return new RetentionHoldResponse(
                    hold.holdReference(),
                    hold.active(),
                    hold.reasonCode(),
                    hold.placedBy(),
                    hold.placedAt().toString(),
                    hold.releasedBy(),
                    hold.releasedAt() == null ? null : hold.releasedAt().toString(),
                    hold.releaseReasonCode(),
                    changed);
        }
    }
}
