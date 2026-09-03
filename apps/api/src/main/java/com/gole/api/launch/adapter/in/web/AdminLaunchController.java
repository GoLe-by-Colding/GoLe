package com.gole.api.launch.adapter.in.web;

import com.gole.api.admin.adapter.in.web.AdminActor;
import com.gole.api.admin.application.port.in.RecordAdminActionUseCase;
import com.gole.api.admin.application.port.in.RecordAdminActionUseCase.RecordAdminActionCommand;
import com.gole.api.admin.domain.model.AdminActionType;
import com.gole.api.admin.domain.model.AdminTargetType;
import com.gole.api.launch.adapter.in.web.LaunchDtos.AdminLaunchConfigResponse;
import com.gole.api.launch.adapter.in.web.LaunchDtos.ChangeStageRequest;
import com.gole.api.launch.adapter.in.web.LaunchDtos.FeatureOverrideRequest;
import com.gole.api.launch.adapter.in.web.LaunchDtos.LaunchChangeRow;
import com.gole.api.launch.adapter.in.web.LaunchDtos.ReadinessCheckRequest;
import com.gole.api.launch.application.port.in.GetLaunchConfigUseCase;
import com.gole.api.launch.application.port.in.ManageLaunchConfigUseCase;
import com.gole.api.launch.application.port.in.ManageLaunchConfigUseCase.ChangeStageCommand;
import com.gole.api.launch.application.port.in.ManageLaunchConfigUseCase.ReadinessChangeResult;
import com.gole.api.launch.application.port.in.ManageLaunchConfigUseCase.SetFeatureOverrideCommand;
import com.gole.api.launch.application.port.in.ManageLaunchConfigUseCase.SetReadinessCheckCommand;
import com.gole.api.launch.application.port.in.ManageLaunchConfigUseCase.StageChangeResult;
import com.gole.api.launch.application.port.out.LaunchSettlementModePort;
import com.gole.api.launch.domain.model.LaunchConfig;
import com.gole.api.launch.domain.model.LaunchFeature;
import com.gole.api.launch.domain.model.LaunchReadinessCheck;
import com.gole.api.launch.domain.model.LaunchStage;
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
 * 공개 단계 운영 API.
 *
 * <p>경로가 {@code /api/admin/**} 이므로 {@code AdminAuthInterceptor} 가 세션을 해석해 ADMIN 만
 * 통과시킨다. 이 컨트롤러는 권한을 다시 확인하지 않는다 — 인증 로직이 인터셉터 한 곳에만
 * 있어야 한다는 기존 규칙을 따른다. 조치자는 인터셉터가 넣어준 요청 속성에서만 읽는다.
 */
@Tag(name = "Admin · 공개 단계", description = "서비스 공개 단계와 기능별 개방 관리")
@RestController
@RequestMapping("/api/admin/launch")
public class AdminLaunchController {

    private final GetLaunchConfigUseCase getLaunchConfig;
    private final ManageLaunchConfigUseCase manageLaunchConfig;
    private final RecordAdminActionUseCase audit;
    private final LaunchSettlementModePort settlementMode;

    public AdminLaunchController(
            GetLaunchConfigUseCase getLaunchConfig,
            ManageLaunchConfigUseCase manageLaunchConfig,
            RecordAdminActionUseCase audit,
            LaunchSettlementModePort settlementMode) {
        this.getLaunchConfig = getLaunchConfig;
        this.manageLaunchConfig = manageLaunchConfig;
        this.audit = audit;
        this.settlementMode = settlementMode;
    }

    @Operation(summary = "현재 공개 단계 조회", description = "공개 응답에 override 원본과 마지막 조치자를 더해 돌려준다.")
    @GetMapping
    public AdminLaunchConfigResponse current() {
        LaunchConfig current = getLaunchConfig.current();
        return response(current, current);
    }

    @Operation(summary = "공개 단계 변경", description = "사유가 필수다. 결제가 새로 열리는 전이는 PortOne 설정이 준비되지 않으면 거부된다.")
    @PostMapping("/stage")
    public AdminLaunchConfigResponse changeStage(
            @Valid @RequestBody ChangeStageRequest request, HttpServletRequest http) {
        AdminActor actor = AdminActor.of(http);
        StageChangeResult result = manageLaunchConfig.changeStageWithResult(new ChangeStageCommand(
                LaunchStage.ofLevel(request.stage()), request.reason(), actor.id(), actor.email()));
        LaunchConfig updated = result.config();
        // 실제 상태가 바뀐 성공만 감사 기록을 남긴다. 같은 단계 재요청은 성공 응답이지만
        // 운영 조치가 아니므로 이력과 관리자 감사 로그 모두 늘리지 않는다.
        if (result.changed()) {
            record(
                    actor,
                    AdminActionType.LAUNCH_STAGE_CHANGE,
                    "stage=" + updated.stage().level(),
                    request.reason());
        }
        return current();
    }

    @Operation(summary = "기능 개방 override", description = "enabled 를 비우면 override 를 해제하고 단계 기본값으로 되돌린다. 사유는 필수다.")
    @PostMapping("/features/{feature}")
    public AdminLaunchConfigResponse setFeature(
            @PathVariable String feature, @Valid @RequestBody FeatureOverrideRequest request, HttpServletRequest http) {
        AdminActor actor = AdminActor.of(http);
        LaunchFeature target = LaunchFeature.of(feature);
        LaunchConfig updated = manageLaunchConfig.setFeatureOverride(
                new SetFeatureOverrideCommand(target, request.enabled(), request.reason(), actor.id(), actor.email()));
        record(
                actor,
                AdminActionType.LAUNCH_STAGE_CHANGE,
                target.apiName() + "=" + updated.isEnabled(target),
                request.reason());
        return current();
    }

    @Operation(summary = "운영 준비 항목 확인", description = "사업·법무·실거래 검증 결과를 사유와 함께 저장한다. 필수 확인을 취소하면 Stage 1로 안전 잠금한다.")
    @PostMapping("/readiness/{check}")
    public AdminLaunchConfigResponse setReadiness(
            @PathVariable String check, @Valid @RequestBody ReadinessCheckRequest request, HttpServletRequest http) {
        AdminActor actor = AdminActor.of(http);
        LaunchReadinessCheck target = LaunchReadinessCheck.of(check);
        ReadinessChangeResult result = manageLaunchConfig.setReadinessCheck(
                new SetReadinessCheckCommand(target, request.confirmed(), request.reason(), actor.id(), actor.email()));
        if (result.changed()) {
            record(
                    actor,
                    AdminActionType.LAUNCH_READINESS_CHANGE,
                    target.apiName() + "=" + request.confirmed(),
                    request.reason());
        }
        return current();
    }

    @Operation(summary = "공개 단계 변경 이력", description = "무엇이 무엇으로 바뀌었는지와 사유를 최신순으로 돌려준다.")
    @GetMapping("/history")
    public List<LaunchChangeRow> history(@RequestParam(value = "limit", defaultValue = "50") int limit) {
        return manageLaunchConfig.history(limit).stream()
                .map(LaunchChangeRow::from)
                .toList();
    }

    private void record(AdminActor actor, AdminActionType type, String targetId, String reason) {
        audit.record(new RecordAdminActionCommand(
                actor.id(), actor.email(), type, AdminTargetType.LAUNCH_CONFIG, targetId, reason));
    }

    private AdminLaunchConfigResponse response(LaunchConfig effective, LaunchConfig requested) {
        return AdminLaunchConfigResponse.from(
                effective, requested, settlementMode.currentMode(), settlementMode.payoutContractVerified());
    }
}
