package com.gole.api.promotion.adapter.in.web;

import com.gole.api.common.exception.BadRequestException;
import com.gole.api.promotion.application.port.in.RecordPromotionRunUseCase;
import com.gole.api.promotion.application.port.in.RecordPromotionRunUseCase.RecordRunCommand;
import com.gole.api.promotion.application.port.in.RecordPromotionRunUseCase.RecordedRun;
import com.gole.api.promotion.domain.model.ModelCall;
import com.gole.api.promotion.domain.model.PromotionCategory;
import com.gole.api.promotion.domain.model.PromotionRun;
import com.gole.api.promotion.domain.model.RunOutcome;
import com.gole.api.promotion.domain.model.RunReasonCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin · 홍보 에이전트 실행 원장(promotion-review D23). 러너가 실행 끝에 한 줄을 남기고, 관리자는
 * 최근 실행을 본다. 공개 Actions 로그에는 결과·사유 코드만 나가고 사유 원문·사용량은 여기에만 쌓인다.
 */
@Tag(name = "Admin · 홍보 실행 원장", description = "홍보 초안 에이전트 실행 결과·사유·모델 사용량 기록")
@RestController
@RequestMapping("/api/admin/promotion-runs")
public class AdminPromotionRunController {

    private final RecordPromotionRunUseCase runs;

    public AdminPromotionRunController(RecordPromotionRunUseCase runs) {
        this.runs = runs;
    }

    @Operation(summary = "실행 기록", description = "같은 runKey 로 다시 보내면 처음 기록을 돌려준다(200). 새로 남기면 201.")
    @PostMapping
    public ResponseEntity<PromotionRun> record(@Valid @RequestBody RecordRunRequest request) {
        RecordedRun recorded;
        try {
            recorded = runs.record(request.toCommand());
        } catch (IllegalArgumentException invalid) {
            // 필드 하나로 못 거르는 조합(SUBMITTED 인데 초안 없음, 캐시 > 입력)은 도메인이 거부한다.
            throw new BadRequestException("INVALID_PROMOTION_RUN", invalid.getMessage());
        }
        return ResponseEntity.status(recorded.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(recorded.run());
    }

    @Operation(summary = "최근 실행", description = "기록 시각 최신순. limit 은 1~100.")
    @GetMapping
    public List<PromotionRun> list(@RequestParam(defaultValue = "50") int limit) {
        return runs.listRecent(limit);
    }

    /**
     * @param detail 모델이 쓴 사유 원문. 저장할 때 {@value PromotionRun#MAX_DETAIL}자로 자르므로 요청은 넉넉히 받는다
     */
    public record RecordRunRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,63}")
            String runKey,

            @NotNull PromotionCategory category,
            @Pattern(regexp = "[0-9a-f]{40}") String sourceCommitSha,
            @NotNull RunOutcome outcome,
            @NotNull RunReasonCode reasonCode,
            @Size(max = 2000) String detail,
            @Size(max = 80) String promotionPostId,
            @Pattern(regexp = "[0-9a-f]{7,40}") String agentSha,

            @Size(max = 300) @Pattern(regexp = "https://github\\.com/.*")
            String runUrl,

            @Size(max = PromotionRun.MAX_CALLS) List<@Valid @NotNull CallRequest> calls) {

        RecordRunCommand toCommand() {
            return new RecordRunCommand(
                    runKey,
                    category,
                    sourceCommitSha,
                    outcome,
                    reasonCode,
                    detail,
                    promotionPostId,
                    agentSha,
                    runUrl,
                    calls == null
                            ? List.of()
                            : calls.stream().map(CallRequest::toModelCall).toList());
        }
    }

    public record CallRequest(
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9-]{0,15}")
            String engine,

            @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:,-]{0,255}")
            String model,

            boolean ok,
            @PositiveOrZero long inputTokens,
            @PositiveOrZero long cachedInputTokens,
            @PositiveOrZero long outputTokens,
            @PositiveOrZero Double costUsd,
            @PositiveOrZero Long durationMs) {

        ModelCall toModelCall() {
            return new ModelCall(engine, model, ok, inputTokens, cachedInputTokens, outputTokens, costUsd, durationMs);
        }
    }
}
