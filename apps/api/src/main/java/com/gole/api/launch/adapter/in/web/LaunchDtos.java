package com.gole.api.launch.adapter.in.web;

import com.gole.api.launch.application.port.out.LaunchSettlementModePort.Mode;
import com.gole.api.launch.domain.model.LaunchConfig;
import com.gole.api.launch.domain.model.LaunchConfigChange;
import com.gole.api.launch.domain.model.LaunchFeature;
import com.gole.api.launch.domain.model.LaunchReadinessCheck;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** 공개 설정 API의 요청·응답 모델. */
public final class LaunchDtos {

    private LaunchDtos() {}

    /**
     * 공개 응답. <b>프론트와 고정된 계약</b>이므로 필드를 빼거나 이름을 바꾸지 않는다.
     * 추가는 가능하다(프론트는 모르는 필드를 무시한다).
     *
     * <p>{@code updatedAt} 은 설정이 한 번도 저장되지 않았으면 {@code null} 이다.
     *
     * <p>{@code tradeMode} 는 단계에서 파생된다 — {@code DIRECT_CHAT}(0~1단계, 플랫폼이 돈을
     * 만지지 않는 직거래), {@code MANUAL_SETTLEMENT}(2단계), {@code PARTNER_PAYOUT}(3단계).
     * 프론트는 이 값으로 "주문/결제 화면을 아예 그리지 않는다"를 판단한다.
     */
    public record LaunchConfigResponse(int stage, String tradeMode, Features features, Instant updatedAt) {

        public static LaunchConfigResponse from(LaunchConfig config) {
            return new LaunchConfigResponse(
                    config.stage().level(),
                    config.tradeMode().name(),
                    new Features(
                            config.isEnabled(LaunchFeature.PAYMENTS),
                            config.isEnabled(LaunchFeature.REVIEWS),
                            config.isEnabled(LaunchFeature.PARTNER_PAYOUT)),
                    config.updatedAt());
        }
    }

    /** 기능 개방 여부. 키 이름은 {@link LaunchFeature#apiName()} 과 일치해야 한다. */
    public record Features(boolean payments, boolean reviews, boolean partnerPayout) {}

    /**
     * 관리자 조회 응답. 공개 응답에 운영 메타(override 원본, 조치자)를 더한다.
     *
     * <p>공개 응답을 그대로 품어서, 관리자가 보는 값과 사용자가 보는 값이 어긋날 수 없게 한다.
     * {@code requestedStage}도 안전 래치를 통과한 영속 값이라 실행 조건 복구만으로 자동 상향되지 않는다.
     */
    public record AdminLaunchConfigResponse(
            LaunchConfigResponse config,
            int requestedStage,
            java.util.Map<String, Boolean> overrides,
            java.util.Map<String, Boolean> readiness,
            String updatedBy,
            String settlementMode,
            boolean payoutContractVerified) {

        public static AdminLaunchConfigResponse from(
                LaunchConfig effective, LaunchConfig requested, Mode settlementMode, boolean payoutContractVerified) {
            java.util.Map<String, Boolean> overrides = new java.util.LinkedHashMap<>();
            requested.overrides().forEach((feature, enabled) -> overrides.put(feature.apiName(), enabled));
            java.util.Map<String, Boolean> readiness = new java.util.LinkedHashMap<>();
            for (LaunchReadinessCheck check : LaunchReadinessCheck.values()) {
                readiness.put(check.apiName(), requested.isConfirmed(check));
            }
            return new AdminLaunchConfigResponse(
                    LaunchConfigResponse.from(effective),
                    requested.stage().level(),
                    overrides,
                    readiness,
                    requested.updatedBy(),
                    settlementMode.name(),
                    payoutContractVerified);
        }
    }

    /** 단계 변경 요청. 사유는 필수다. */
    public record ChangeStageRequest(
            @NotNull(message = "공개 단계를 지정해야 합니다") @Min(0) @Max(3) Integer stage,
            @NotBlank(message = "변경 사유를 입력해야 합니다") @Size(max = 500) String reason) {}

    /**
     * 기능 override 요청.
     *
     * @param enabled {@code null} 이면 override 를 해제하고 단계 기본값으로 되돌린다.
     */
    public record FeatureOverrideRequest(
            Boolean enabled, @NotBlank(message = "변경 사유를 입력해야 합니다") @Size(max = 500) String reason) {}

    /** 서버가 자동 판정할 수 없는 운영 준비 항목 확인 또는 확인 취소 요청. */
    public record ReadinessCheckRequest(
            @NotNull(message = "확인 여부를 지정해야 합니다") Boolean confirmed,
            @NotBlank(message = "변경 사유를 입력해야 합니다") @Size(max = 500) String reason) {}

    /** 변경 이력 1행. */
    public record LaunchChangeRow(
            String id,
            String type,
            String target,
            String before,
            String after,
            String reason,
            String actorId,
            String actorEmail,
            Instant occurredAt) {

        public static LaunchChangeRow from(LaunchConfigChange change) {
            return new LaunchChangeRow(
                    change.id(),
                    change.type().name(),
                    change.target(),
                    change.before(),
                    change.after(),
                    change.reason(),
                    change.actorId(),
                    change.actorEmail(),
                    change.occurredAt());
        }
    }
}
