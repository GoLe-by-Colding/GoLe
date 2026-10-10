package com.gole.api.admin.adapter.in.web;

import com.gole.api.admin.application.port.in.RecordAdminActionUseCase;
import com.gole.api.admin.application.port.in.RecordAdminActionUseCase.RecordAdminActionCommand;
import com.gole.api.admin.domain.model.AdminActionType;
import com.gole.api.admin.domain.model.AdminTargetType;
import com.gole.api.common.exception.BadRequestException;
import com.gole.api.promotion.application.port.in.ManagePromotionMemoryUseCase;
import com.gole.api.promotion.application.port.in.ManagePromotionMemoryUseCase.*;
import com.gole.api.promotion.domain.model.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin · 홍보 메모리", description = "반려 경험과 사람이 확정하는 홍보 지침")
@RestController
@RequestMapping("/api/admin")
public class AdminPromotionMemoryController {
    private final ManagePromotionMemoryUseCase memory;
    private final RecordAdminActionUseCase audit;

    public AdminPromotionMemoryController(ManagePromotionMemoryUseCase memory, RecordAdminActionUseCase audit) {
        this.memory = memory;
        this.audit = audit;
    }

    @Operation(summary = "이번 실행의 작업 기억", description = "관련 반려 3건, 활성 지침 8개, 미처리 반려 3건")
    @GetMapping("/promotion-memory/context")
    public WorkingMemory context(
            @RequestParam(defaultValue = "FEATURE") PromotionCategory category,
            @RequestParam(required = false) List<String> routes) {
        return validated(() -> memory.context(category, routes));
    }

    @GetMapping("/promotion-feedback")
    public List<PromotionFeedback> feedback(
            @RequestParam(required = false) String postId, @RequestParam(defaultValue = "50") int limit) {
        return memory.listFeedback(postId, limit);
    }

    @Operation(summary = "반려 경험 단건 조회", description = "최근 목록 범위 밖의 지침 근거도 ID로 조회")
    @GetMapping("/promotion-feedback/{id}")
    public PromotionFeedback feedback(@PathVariable String id) {
        return memory.getFeedback(id);
    }

    @Operation(summary = "반려 경험의 지침 제안", description = "같은 실행 재시도는 처음 결과 반환. 빈 제안도 처리 완료")
    @PostMapping("/promotion-memory/reflect")
    public List<PromotionGuideline> reflect(@Valid @RequestBody ReflectRequest request, HttpServletRequest http) {
        return validated(() -> memory.reflect(
                AdminActor.of(http).id(),
                new ReflectionCommand(
                        request.feedbackIds(),
                        request.runKey(),
                        request.proposals().stream()
                                .map(ProposalRequest::toProposal)
                                .toList())));
    }

    @GetMapping("/promotion-guidelines")
    public List<PromotionGuideline> guidelines(
            @RequestParam(required = false) PromotionGuidelineStatus status,
            @RequestParam(defaultValue = "50") int limit) {
        return memory.listGuidelines(status, limit);
    }

    @PatchMapping("/promotion-guidelines/{id}")
    public PromotionGuideline edit(
            @PathVariable String id, @Valid @RequestBody EditRequest request, HttpServletRequest http) {
        PromotionGuideline result =
                validated(() -> memory.edit(id, request.content(), request.targets(), request.categories()));
        record(http, AdminActionType.PROMOTION_GUIDELINE_EDIT, id);
        return result;
    }

    @PostMapping("/promotion-guidelines/{id}/activate")
    public PromotionGuideline activate(@PathVariable String id, HttpServletRequest http) {
        PromotionGuideline result = memory.activate(id, AdminActor.of(http).id());
        record(http, AdminActionType.PROMOTION_GUIDELINE_ACTIVATE, id);
        return result;
    }

    @PostMapping("/promotion-guidelines/{id}/dismiss")
    public PromotionGuideline dismiss(@PathVariable String id, HttpServletRequest http) {
        PromotionGuideline result = memory.dismiss(id);
        record(http, AdminActionType.PROMOTION_GUIDELINE_DISMISS, id);
        return result;
    }

    @PostMapping("/promotion-guidelines/{id}/retire")
    public PromotionGuideline retire(@PathVariable String id, HttpServletRequest http) {
        PromotionGuideline result = memory.retire(id);
        record(http, AdminActionType.PROMOTION_GUIDELINE_RETIRE, id);
        return result;
    }

    private void record(HttpServletRequest http, AdminActionType type, String id) {
        AdminActor actor = AdminActor.of(http);
        audit.record(new RecordAdminActionCommand(
                actor.id(), actor.email(), type, AdminTargetType.PROMOTION_GUIDELINE, id, null));
    }

    private static <T> T validated(Supplier<T> action) {
        try {
            return action.get();
        } catch (IllegalArgumentException invalid) {
            throw new BadRequestException("INVALID_PROMOTION_MEMORY", invalid.getMessage());
        }
    }

    public record ReflectRequest(
            @NotNull @Size(min = 1, max = 3) List<@NotBlank @Size(max = 80) String> feedbackIds,

            @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,63}")
            String runKey,

            @NotNull @Size(max = 3) List<@NotNull @Valid ProposalRequest> proposals) {}

    public record ProposalRequest(
            @NotNull PromotionGuidelineKind kind,

            @NotBlank @Size(max = PromotionGuideline.MAX_CONTENT)
            String content,

            @NotNull @Size(min = 1, max = 3) List<@NotNull PromotionMemoryTarget> targets,
            @NotNull @Size(min = 1, max = 2) List<@NotNull PromotionCategory> categories,
            @NotNull @Size(min = 1, max = 3) List<@NotBlank @Size(max = 80) String> sourceFeedbackIds) {
        Proposal toProposal() {
            return new Proposal(kind, content, targets, categories, sourceFeedbackIds);
        }
    }

    public record EditRequest(
            @NotBlank @Size(max = PromotionGuideline.MAX_CONTENT)
            String content,

            @NotNull @Size(min = 1, max = 3) List<@NotNull PromotionMemoryTarget> targets,
            @NotNull @Size(min = 1, max = 2) List<@NotNull PromotionCategory> categories) {}
}
