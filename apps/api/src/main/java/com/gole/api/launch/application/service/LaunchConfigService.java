package com.gole.api.launch.application.service;

import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.exception.ConflictException;
import com.gole.api.launch.application.port.in.GetLaunchConfigUseCase;
import com.gole.api.launch.application.port.in.ManageLaunchConfigUseCase;
import com.gole.api.launch.application.port.in.ManageLaunchConfigUseCase.ReadinessChangeResult;
import com.gole.api.launch.application.port.in.ManageLaunchConfigUseCase.StageChangeResult;
import com.gole.api.launch.application.port.out.LaunchConfigHistoryPort;
import com.gole.api.launch.application.port.out.LaunchConfigRepositoryPort;
import com.gole.api.launch.application.port.out.LaunchSettlementModePort;
import com.gole.api.launch.application.port.out.LaunchSettlementModePort.Mode;
import com.gole.api.launch.domain.model.LaunchConfig;
import com.gole.api.launch.domain.model.LaunchConfigChange;
import com.gole.api.launch.domain.model.LaunchFeature;
import com.gole.api.launch.domain.model.LaunchReadinessCheck;
import com.gole.api.launch.domain.model.LaunchStage;
import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase;
import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase.ConfigurationIssue;
import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase.Snapshot;
import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase.State;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 단계와 기능별 override 를 관리한다.
 *
 * <p>핵심 규칙은 두 가지다.
 *
 * <ul>
 *   <li><b>사유 없는 변경은 없다.</b> 서비스를 열고 닫은 이유는 사후에 반드시 필요하다.
 *   <li><b>결제를 여는 전이는 fail-closed 다.</b> PortOne 설정이 실제로 준비되지 않았는데
 *       단계만 올리면, 사용자에게는 결제 버튼이 보이는데 결제가 실패하는 최악의 상태가 된다.
 *       그래서 준비 상태를 확인하고 아니면 전이를 거부한다 — 경고가 아니라 거부다.
 * </ul>
 */
@Service
public class LaunchConfigService implements GetLaunchConfigUseCase, ManageLaunchConfigUseCase {

    private static final Logger log = LoggerFactory.getLogger(LaunchConfigService.class);
    private static final int MAX_HISTORY = 200;
    private static final int MAX_REASON_LENGTH = 500;
    private final LaunchConfigRepositoryPort repository;
    private final LaunchConfigHistoryPort history;
    private final GetPaymentReadinessUseCase paymentReadiness;
    private final LaunchSettlementModePort settlementMode;
    private final LaunchConfigSafetyClamp safetyClamp;
    private final Clock clock;

    public LaunchConfigService(
            LaunchConfigRepositoryPort repository,
            LaunchConfigHistoryPort history,
            GetPaymentReadinessUseCase paymentReadiness,
            LaunchSettlementModePort settlementMode,
            LaunchConfigSafetyClamp safetyClamp,
            Clock clock) {
        this.repository = repository;
        this.history = history;
        this.paymentReadiness = paymentReadiness;
        this.settlementMode = settlementMode;
        this.safetyClamp = safetyClamp;
        this.clock = clock;
    }

    @Override
    public LaunchConfig current() {
        return safetyClamp.enforce();
    }

    @Override
    public LaunchConfig requested() {
        // 요청값도 실행값과 같은 안전 래치를 통과시킨다. 높은 희망 단계를 따로 남겨두면
        // 환경 복구만으로 결제가 감사 기록 없이 자동 재개될 수 있다.
        return safetyClamp.enforce();
    }

    @Override
    @Transactional
    public LaunchConfig changeStage(ChangeStageCommand command) {
        return applyStageChange(command).config();
    }

    @Override
    @Transactional
    public StageChangeResult changeStageWithResult(ChangeStageCommand command) {
        return applyStageChange(command);
    }

    private StageChangeResult applyStageChange(ChangeStageCommand command) {
        if (command.stage() == null) {
            throw new BadRequestException("LAUNCH_STAGE_REQUIRED", "변경할 공개 단계를 지정해야 합니다");
        }
        String reason = requireReason(command.reason());
        LaunchConfig before = safetyClamp.enforce();
        if (before.stage() == command.stage()) {
            // 같은 단계로의 재요청은 이력을 늘리지 않는다. 감사 로그가 의미 없는 줄로 덮이면
            // 정작 필요한 변경을 찾지 못한다.
            return new StageChangeResult(before, false);
        }
        requireCompatibleSettlementMode(command.stage());
        if (command.stage().level() > before.stage().level()) {
            requireOperationalReadiness(before, command.stage());
        }
        // 결제가 새로 열리는 방향일 때만 검증한다. 단계를 내리는 조치(사고 대응)는 막지 않는다.
        if (opensPayments(before, command.stage())) {
            requirePaymentReadiness();
        }

        Instant now = Instant.now(clock);
        LaunchConfig candidate = before.withStage(command.stage(), now, command.actorId());
        LaunchConfig after = savedOrCandidate(repository.save(candidate), candidate);
        history.append(new LaunchConfigChange(
                UUID.randomUUID().toString(),
                LaunchConfigChange.Type.STAGE,
                "stage",
                Integer.toString(before.stage().level()),
                Integer.toString(after.stage().level()),
                reason,
                command.actorId(),
                command.actorEmail(),
                now));
        log.info("공개 단계 변경 {} -> {} actor={} reason={}", before.stage(), after.stage(), command.actorId(), reason);
        return new StageChangeResult(after, true);
    }

    @Override
    @Transactional
    public LaunchConfig setFeatureOverride(SetFeatureOverrideCommand command) {
        if (command.feature() == null) {
            throw new BadRequestException("LAUNCH_FEATURE_REQUIRED", "변경할 기능을 지정해야 합니다");
        }
        String reason = requireReason(command.reason());
        LaunchConfig before = safetyClamp.enforce();
        boolean wasEnabled = before.isEnabled(command.feature());
        LaunchConfig projected =
                before.withOverride(command.feature(), command.enabled(), before.updatedAt(), before.updatedBy());
        boolean becomesEnabled = !wasEnabled && projected.isEnabled(command.feature());

        if (Boolean.TRUE.equals(command.enabled())) {
            if (command.feature() == LaunchFeature.PAYMENTS && !before.stage().atLeast(LaunchStage.TRADING)) {
                throw new ConflictException("LAUNCH_STAGE_REQUIRED_FOR_PAYMENTS", "결제는 Stage 2 이상에서만 열 수 있습니다");
            }
        }

        // false override 해제(null)로 단계 기본값이 되살아나는 것도 기능을 여는 전이다.
        if (command.feature() == LaunchFeature.PARTNER_PAYOUT
                && (Boolean.TRUE.equals(command.enabled()) || becomesEnabled)) {
            requirePartnerPayoutPrerequisites(before);
        }
        if (command.feature() == LaunchFeature.PAYMENTS && becomesEnabled) {
            requireOperationalReadiness(before, LaunchStage.TRADING);
            requirePaymentReadiness();
        }

        Instant now = Instant.now(clock);
        LaunchConfig candidate = before.withOverride(command.feature(), command.enabled(), now, command.actorId());
        LaunchConfig after = savedOrCandidate(repository.save(candidate), candidate);
        history.append(new LaunchConfigChange(
                UUID.randomUUID().toString(),
                LaunchConfigChange.Type.FEATURE_OVERRIDE,
                command.feature().apiName(),
                describe(wasEnabled, before.overrides().get(command.feature())),
                describe(after.isEnabled(command.feature()), command.enabled()),
                reason,
                command.actorId(),
                command.actorEmail(),
                now));
        log.info(
                "기능 override 변경 {} {} -> {} actor={} reason={}",
                command.feature().apiName(),
                wasEnabled,
                after.isEnabled(command.feature()),
                command.actorId(),
                reason);
        return after;
    }

    @Override
    @Transactional
    public ReadinessChangeResult setReadinessCheck(SetReadinessCheckCommand command) {
        if (command.check() == null) {
            throw new BadRequestException("LAUNCH_READINESS_REQUIRED", "변경할 운영 준비 항목을 지정해야 합니다");
        }
        String reason = requireReason(command.reason());
        LaunchConfig before = safetyClamp.enforce();
        boolean wasConfirmed = before.isConfirmed(command.check());
        if (wasConfirmed == command.confirmed()) {
            return new ReadinessChangeResult(before, false, false);
        }

        Instant now = Instant.now(clock);
        LaunchConfig candidate = before.withReadiness(command.check(), command.confirmed(), now, command.actorId());
        boolean safetyLowered = !candidate.hasRequiredReadiness(candidate.stage());
        if (safetyLowered) {
            candidate = candidate.withStage(LaunchStage.BROWSE_ONLY, now, command.actorId());
        }
        LaunchConfig after = savedOrCandidate(repository.save(candidate), candidate);
        history.append(new LaunchConfigChange(
                UUID.randomUUID().toString(),
                LaunchConfigChange.Type.READINESS,
                command.check().apiName(),
                Boolean.toString(wasConfirmed),
                Boolean.toString(command.confirmed()),
                reason,
                command.actorId(),
                command.actorEmail(),
                now));
        if (safetyLowered) {
            history.append(new LaunchConfigChange(
                    UUID.randomUUID().toString(),
                    LaunchConfigChange.Type.STAGE,
                    "stage",
                    Integer.toString(before.stage().level()),
                    Integer.toString(after.stage().level()),
                    "운영 준비 확인 취소로 Stage 1에 안전 잠금: " + command.check().apiName(),
                    command.actorId(),
                    command.actorEmail(),
                    now));
        }
        log.info(
                "운영 준비 확인 변경 {} {} -> {} actor={} safetyLowered={} reason={}",
                command.check().apiName(),
                wasConfirmed,
                command.confirmed(),
                command.actorId(),
                safetyLowered,
                reason);
        return new ReadinessChangeResult(after, true, safetyLowered);
    }

    @Override
    public List<LaunchConfigChange> history(int limit) {
        return history.findRecent(Math.max(1, Math.min(limit, MAX_HISTORY)));
    }

    /** 이 전이로 결제가 닫힌 상태에서 열린 상태가 되는가. */
    private static boolean opensPayments(LaunchConfig before, LaunchStage target) {
        if (before.isEnabled(LaunchFeature.PAYMENTS)) {
            return false;
        }
        LaunchConfig projected = new LaunchConfig(
                target,
                before.overrides(),
                before.readiness(),
                before.updatedAt(),
                before.updatedBy(),
                before.version());
        return projected.isEnabled(LaunchFeature.PAYMENTS);
    }

    private void requireOperationalReadiness(LaunchConfig config, LaunchStage target) {
        List<String> missing = java.util.Arrays.stream(LaunchReadinessCheck.values())
                .filter(check -> check.requiredAt(target) && !config.isConfirmed(check))
                .map(LaunchReadinessCheck::apiName)
                .toList();
        if (!missing.isEmpty()) {
            throw new ConflictException(
                    "LAUNCH_OPERATIONAL_READINESS_REQUIRED",
                    "운영 준비 확인이 끝나지 않아 이 단계나 기능을 열 수 없습니다 (미확인=%s)".formatted(String.join(", ", missing)));
        }
    }

    private void requireCompatibleSettlementMode(LaunchStage target) {
        Mode mode = settlementMode.currentMode();
        if (target.atLeast(LaunchStage.TRADING) && !settlementMode.payoutContractVerified()) {
            throw new ConflictException(
                    "LAUNCH_PAYOUT_CONTRACT_REQUIRED", "Stage 2 이상은 현재 도메인·거래 모델에 대한 PG/지급대행 계약 확인이 필요합니다");
        }
        if (target == LaunchStage.TRADING && mode != Mode.MANUAL) {
            throw new ConflictException(
                    "LAUNCH_MANUAL_SETTLEMENT_REQUIRED", "Stage 2는 수동 정산 모드에서만 열 수 있습니다 (현재 모드 %s)".formatted(mode));
        }
        if (target == LaunchStage.FULL && mode != Mode.PROVIDER) {
            throw new ConflictException(
                    "LAUNCH_PROVIDER_MODE_REQUIRED", "Stage 3은 지급대행 모드에서만 열 수 있습니다 (현재 모드 %s)".formatted(mode));
        }
    }

    private void requirePartnerPayoutPrerequisites(LaunchConfig before) {
        if (before.stage() != LaunchStage.FULL || settlementMode.currentMode() != Mode.PROVIDER) {
            throw new ConflictException("LAUNCH_PROVIDER_MODE_REQUIRED", "자동 지급은 Stage 3과 지급대행 모드가 모두 준비돼야 열 수 있습니다");
        }
        if (!before.isEnabled(LaunchFeature.PAYMENTS)) {
            throw new ConflictException("LAUNCH_PAYMENTS_REQUIRED", "결제가 닫힌 상태에서는 자동 지급을 열 수 없습니다");
        }
        requireOperationalReadiness(before, LaunchStage.FULL);
    }

    /**
     * 결제 설정이 실제로 준비됐는지 확인하고, 아니면 전이를 거부한다(fail-closed).
     *
     * <p>비밀값은 메시지에 담지 않는다 — 어떤 설정이 비었는지 이름만 알려준다.
     */
    private void requirePaymentReadiness() {
        Snapshot snapshot;
        try {
            snapshot = paymentReadiness.getPaymentReadiness();
        } catch (RuntimeException readinessFailure) {
            log.error("결제 준비 상태 조회 실패 — 결제 개방 전이를 거부함", readinessFailure);
            throw new ConflictException("LAUNCH_PAYMENT_NOT_READY", "결제 준비 상태를 확인할 수 없어 이 단계로 올릴 수 없습니다");
        }
        if (isReady(snapshot)) {
            return;
        }
        String detail = snapshot == null
                ? "결제 준비 상태를 확인할 수 없습니다"
                : "결제 준비 상태=%s%s".formatted(snapshot.state(), issues(snapshot));
        throw new ConflictException(
                "LAUNCH_PAYMENT_NOT_READY", "결제 설정이 준비되지 않아 이 단계로 올릴 수 없습니다 (%s)".formatted(detail));
    }

    private static boolean isReady(Snapshot snapshot) {
        return snapshot != null && snapshot.ready() && snapshot.state() == State.READY;
    }

    private static String issues(Snapshot snapshot) {
        List<ConfigurationIssue> issues = snapshot.issues();
        if (issues == null || issues.isEmpty()) {
            return "";
        }
        return ", 문제 설정="
                + issues.stream()
                        .map(issue -> issue.setting() + "(" + issue.problem() + ")")
                        .collect(Collectors.joining(", "));
    }

    private static String requireReason(String reason) {
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.isEmpty()) {
            throw new BadRequestException("LAUNCH_REASON_REQUIRED", "변경 사유를 입력해야 합니다");
        }
        if (trimmed.length() > MAX_REASON_LENGTH) {
            throw new BadRequestException(
                    "LAUNCH_REASON_TOO_LONG", "변경 사유는 %d자를 넘을 수 없습니다".formatted(MAX_REASON_LENGTH));
        }
        return trimmed;
    }

    private static LaunchConfig savedOrCandidate(LaunchConfig saved, LaunchConfig candidate) {
        // Mockito 기반 단위 테스트의 unstubbed save 는 null을 반환한다. 운영 어댑터는 항상
        // 저장 후 낙관적 잠금 버전이 반영된 값을 돌려준다.
        return saved == null ? candidate : saved;
    }

    /** 이력 표기: 최종 개방 여부와, 그것이 override 때문인지 단계 기본인지 함께 남긴다. */
    private static String describe(boolean enabled, Boolean override) {
        return (enabled ? "enabled" : "disabled") + (override == null ? "(stage default)" : "(override)");
    }
}
