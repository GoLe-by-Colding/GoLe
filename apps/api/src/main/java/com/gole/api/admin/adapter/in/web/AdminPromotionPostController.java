package com.gole.api.admin.adapter.in.web;

import com.gole.api.admin.application.port.in.RecordAdminActionUseCase;
import com.gole.api.admin.application.port.in.RecordAdminActionUseCase.RecordAdminActionCommand;
import com.gole.api.admin.domain.model.AdminActionType;
import com.gole.api.admin.domain.model.AdminTargetType;
import com.gole.api.promotion.application.port.in.ConsumePromotionDraftRequestUseCase;
import com.gole.api.promotion.application.port.in.CreatePromotionPostUseCase;
import com.gole.api.promotion.application.port.in.CreatePromotionPostUseCase.CreatePromotionPostCommand;
import com.gole.api.promotion.application.port.in.GetPromotionMetricsUseCase;
import com.gole.api.promotion.application.port.in.GetPromotionMetricsUseCase.PromotionMetrics;
import com.gole.api.promotion.application.port.in.ManagePromotionPostsUseCase;
import com.gole.api.promotion.application.port.in.RecordPromotionPostEvaluationUseCase;
import com.gole.api.promotion.application.port.in.RecordPromotionPostEvaluationUseCase.EvaluationCommand;
import com.gole.api.promotion.application.port.in.RequestPromotionDraftUseCase;
import com.gole.api.promotion.application.port.in.SubmitPromotionPostForReviewUseCase;
import com.gole.api.promotion.domain.model.EvaluationCriterion;
import com.gole.api.promotion.domain.model.EvaluationReasonTag;
import com.gole.api.promotion.domain.model.FirstReviewVerdict;
import com.gole.api.promotion.domain.model.HoldReasonKind;
import com.gole.api.promotion.domain.model.PromotionChannel;
import com.gole.api.promotion.domain.model.PromotionDraftRequest;
import com.gole.api.promotion.domain.model.PromotionPost;
import com.gole.api.promotion.domain.model.PromotionPostEvaluation;
import com.gole.api.promotion.domain.model.PromotionPostStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin · 홍보 게시 검토 — Threads 등 외부 채널 업로드 전 다른 관리자의 승인을 강제한다.
 * (promotion-review)
 *
 * <p>지금 발행(publish)은 {@code StubThreadsPublishAdapter}가 처리하며 실제 외부에 올라가지
 * 않는다(promotion-review D5) — 자격증명이 준비되기 전까지는 승인·발행을 아무리 눌러도 실제
 * Threads 계정에는 나가지 않는다.
 */
@Tag(name = "Admin · 홍보 게시 검토", description = "Threads 등 외부 채널 업로드 전 다른 관리자 승인 게이트")
@RestController
@RequestMapping("/api/admin/promotion-posts")
public class AdminPromotionPostController {

    private final CreatePromotionPostUseCase createPromotionPost;
    private final SubmitPromotionPostForReviewUseCase submitPromotionPost;
    private final ManagePromotionPostsUseCase managePromotionPosts;
    private final RecordPromotionPostEvaluationUseCase recordEvaluation;
    private final GetPromotionMetricsUseCase getMetrics;
    private final RequestPromotionDraftUseCase requestDraft;
    private final ConsumePromotionDraftRequestUseCase consumeDraftRequest;
    private final RecordAdminActionUseCase audit;

    public AdminPromotionPostController(
            CreatePromotionPostUseCase createPromotionPost,
            SubmitPromotionPostForReviewUseCase submitPromotionPost,
            ManagePromotionPostsUseCase managePromotionPosts,
            RecordPromotionPostEvaluationUseCase recordEvaluation,
            GetPromotionMetricsUseCase getMetrics,
            RequestPromotionDraftUseCase requestDraft,
            ConsumePromotionDraftRequestUseCase consumeDraftRequest,
            RecordAdminActionUseCase audit) {
        this.createPromotionPost = createPromotionPost;
        this.submitPromotionPost = submitPromotionPost;
        this.managePromotionPosts = managePromotionPosts;
        this.recordEvaluation = recordEvaluation;
        this.getMetrics = getMetrics;
        this.requestDraft = requestDraft;
        this.consumeDraftRequest = consumeDraftRequest;
        this.audit = audit;
    }

    @Operation(summary = "홍보 게시 초안 등록", description = "DRAFT 상태로 저장. 작성자는 요청한 관리자로 고정된다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> create(@Valid @RequestBody CreatePromotionPostRequest request, HttpServletRequest http) {
        String id = createPromotionPost.create(new CreatePromotionPostCommand(
                AdminActor.of(http).id(),
                request.channel(),
                request.caption(),
                request.mediaKeys(),
                request.sourceCommitSha()));
        return Map.of("id", id);
    }

    @Operation(summary = "검토 요청", description = "DRAFT → PENDING_REVIEW. DRAFT가 아니면 거부된다.")
    @PostMapping("/{id}/submit")
    public PromotionPost submit(@PathVariable String id) {
        return submitPromotionPost.submit(id);
    }

    @Operation(summary = "홍보 게시 목록", description = "상태 필터 없으면 전체 최신순.")
    @GetMapping
    public List<PromotionPost> list(
            @RequestParam(required = false) PromotionPostStatus status, @RequestParam(defaultValue = "50") int limit) {
        return managePromotionPosts.list(status, limit);
    }

    @Operation(summary = "홍보 운영·품질 지표", description = "상태별 건수, 승인/반려/발행 건수, 반려율, 검토 소요시간과 루브릭 집계.")
    @GetMapping("/metrics")
    public PromotionMetrics metrics() {
        return getMetrics.getMetrics();
    }

    @Operation(summary = "홍보 게시 단건 조회")
    @GetMapping("/{id}")
    public PromotionPost get(@PathVariable String id) {
        return managePromotionPosts.get(id);
    }

    @Operation(summary = "품질 평가 기록", description = "게시물당 평가는 1건 — 다시 호출하면 기존 평가를 덮어쓴다. 채점 자체는 사람이 한다.")
    @PutMapping("/{id}/evaluation")
    public PromotionPostEvaluation upsertEvaluation(
            @PathVariable String id, @Valid @RequestBody RecordEvaluationRequest request, HttpServletRequest http) {
        AdminActor actor = AdminActor.of(http);
        return recordEvaluation.upsert(
                id,
                actor.id(),
                new EvaluationCommand(
                        request.criterionScores(),
                        request.verdict(),
                        request.holdReasonKind(),
                        request.reasonTags(),
                        request.factualFixNeeded(),
                        request.reviewSeconds(),
                        request.reviseSeconds(),
                        request.notes()));
    }

    @Operation(summary = "품질 평가 조회", description = "평가가 없으면 404.")
    @GetMapping("/{id}/evaluation")
    public PromotionPostEvaluation getEvaluation(@PathVariable String id) {
        return recordEvaluation.get(id);
    }

    @Operation(summary = "원본 커밋으로 생성된 홍보 게시 존재 여부 조회")
    @GetMapping("/exists")
    public Map<String, Boolean> existsBySourceCommitSha(
            @RequestParam @Pattern(regexp = "[0-9a-f]{40}") String sourceCommitSha) {
        return Map.of("exists", managePromotionPosts.existsBySourceCommitSha(sourceCommitSha));
    }

    @Operation(summary = "승인", description = "PENDING_REVIEW → APPROVED. 작성자 본인은 승인할 수 없다.")
    @PostMapping("/{id}/approve")
    public PromotionPost approve(@PathVariable String id, HttpServletRequest http) {
        AdminActor actor = AdminActor.of(http);
        PromotionPost approved = managePromotionPosts.approve(id, actor.id());
        record(http, AdminActionType.PROMOTION_POST_APPROVE, id, null);
        return approved;
    }

    @Operation(summary = "반려", description = "PENDING_REVIEW → DRAFT. 사유가 남고 작성자가 고쳐 재제출할 수 있다.")
    @PostMapping("/{id}/reject")
    public PromotionPost reject(
            @PathVariable String id, @Valid @RequestBody RejectPromotionPostRequest request, HttpServletRequest http) {
        AdminActor actor = AdminActor.of(http);
        PromotionPost rejected = managePromotionPosts.reject(id, actor.id(), request.reason());
        record(http, AdminActionType.PROMOTION_POST_REJECT, id, request.reason());
        return rejected;
    }

    @Operation(summary = "발행", description = "APPROVED → PUBLISHED. 지금은 스텁 어댑터가 처리해 실제 외부에 올라가지 않는다.")
    @PostMapping("/{id}/publish")
    public PromotionPost publish(@PathVariable String id, HttpServletRequest http) {
        PromotionPost published = managePromotionPosts.publish(id);
        record(http, AdminActionType.PROMOTION_POST_PUBLISH, id, published.getExternalPostId());
        return published;
    }

    @Operation(
            summary = "홍보 초안 생성 요청",
            description =
                    "다음 에이전트 실행이 처리할 요청을 남긴다. 커밋을 비우면 에이전트가 자동 선정한다. " + "즉시 생성이 아니며, 홍보 불가 사유는 이 접수 단계에서 곧바로 돌려준다.")
    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PromotionDraftRequest requestDraft(
            @Valid @RequestBody RequestPromotionDraftRequest request, HttpServletRequest http) {
        AdminActor actor = AdminActor.of(http);
        PromotionDraftRequest accepted = requestDraft.request(request.sourceCommitSha(), actor.id());
        record(http, AdminActionType.PROMOTION_DRAFT_REQUEST, accepted.getId(), accepted.getSourceCommitSha());
        return accepted;
    }

    @Operation(summary = "홍보 초안 요청 목록", description = "최신순. 관리자 화면이 진행 상태를 보여주는 데 쓴다.")
    @GetMapping("/requests")
    public List<PromotionDraftRequest> listDraftRequests(@RequestParam(defaultValue = "20") int limit) {
        return requestDraft.findRecentFirst(limit);
    }

    @Operation(summary = "홍보 초안 요청 점유(에이전트용)", description = "대기 중인 요청 하나를 점유한다. 없으면 204. 사람이 부르는 API 가 아니다.")
    @PostMapping("/requests/claim")
    public ResponseEntity<PromotionDraftRequest> claimDraftRequest() {
        return consumeDraftRequest.claimNext().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent()
                .build());
    }

    @Operation(summary = "홍보 초안 요청 성공 회신(에이전트용)")
    @PostMapping("/requests/{id}/succeed")
    public PromotionDraftRequest succeedDraftRequest(
            @PathVariable String id, @Valid @RequestBody SucceedDraftRequestRequest request) {
        return consumeDraftRequest.succeed(id, request.leaseToken(), request.promotionPostId());
    }

    @Operation(summary = "홍보 초안 요청 실패 회신(에이전트용)", description = "사유는 코드만 받는다 — 예외 원문에는 diff·캡션이 섞일 수 있다.")
    @PostMapping("/requests/{id}/fail")
    public PromotionDraftRequest failDraftRequest(
            @PathVariable String id, @Valid @RequestBody FailDraftRequestRequest request) {
        return consumeDraftRequest.fail(id, request.leaseToken(), request.failureCode());
    }

    private void record(HttpServletRequest http, AdminActionType type, String promotionPostId, String reason) {
        AdminActor actor = AdminActor.of(http);
        audit.record(new RecordAdminActionCommand(
                actor.id(), actor.email(), type, AdminTargetType.PROMOTION_POST, promotionPostId, reason));
    }

    /** @param mediaKeys 업로드 스테이지 키 목록(공개 URL 아님) — {@code POST /api/v1/media/images}로
     *  먼저 올린 뒤 그 응답의 {@code key}를 그대로 담는다. */
    public record CreatePromotionPostRequest(
            @NotNull PromotionChannel channel,
            @NotBlank @Size(max = 500) String caption,
            @Size(max = 10) List<@NotBlank @Size(max = 80) String> mediaKeys,
            @Pattern(regexp = "[0-9a-f]{40}") String sourceCommitSha) {}

    /** @param sourceCommitSha 비우면 에이전트가 지금처럼 자동 선정한다. */
    public record RequestPromotionDraftRequest(
            @Pattern(regexp = "[0-9a-f]{40}") String sourceCommitSha) {}

    public record SucceedDraftRequestRequest(
            @NotBlank @Size(max = 80) String leaseToken,
            @NotBlank @Size(max = 80) String promotionPostId) {}

    public record FailDraftRequestRequest(
            @NotBlank @Size(max = 80) String leaseToken,
            @NotBlank @Size(max = 64) String failureCode) {}

    public record RejectPromotionPostRequest(
            @NotBlank @Size(max = 1000) String reason) {}

    /** @param criterionScores 루브릭 항목별 0~2점. 항목이 빠지면 N/A로 취급한다. */
    public record RecordEvaluationRequest(
            Map<EvaluationCriterion, @Min(0) @Max(2) Integer> criterionScores,
            @NotNull FirstReviewVerdict verdict,
            HoldReasonKind holdReasonKind,
            Set<EvaluationReasonTag> reasonTags,
            Boolean factualFixNeeded,
            @PositiveOrZero Integer reviewSeconds,
            @PositiveOrZero Integer reviseSeconds,
            @Size(max = 2000) String notes) {}
}
