package com.gole.api.launch.application.service;

import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.exception.ConflictException;
import com.gole.api.launch.application.port.in.GetLaunchConfigUseCase;
import com.gole.api.launch.application.port.in.ManageLaunchConfigUseCase;
import com.gole.api.launch.application.port.out.LaunchConfigHistoryPort;
import com.gole.api.launch.application.port.out.LaunchConfigRepositoryPort;
import com.gole.api.launch.application.port.out.LaunchSettlementModePort;
import com.gole.api.launch.application.port.out.LaunchSettlementModePort.Mode;
import com.gole.api.launch.domain.model.LaunchConfig;
import com.gole.api.launch.domain.model.LaunchConfigChange;
import com.gole.api.launch.domain.model.LaunchFeature;
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
    private final Clock clock;

    public LaunchConfigService(
            LaunchConfigRepositoryPort repository,
            LaunchConfigHistoryPort history,
            GetPaymentReadinessUseCase paymentReadiness,
            LaunchSettlementModePort settlementMode,
            Clock clock) {
        this.repository = repository;
        this.history = history;
        this.paymentReadiness = paymentReadiness;
        this.settlementMode = settlementMode;
        this.clock = clock;
    }

    @Override
    public LaunchConfig current() {
        LaunchConfig stored = stored();
        LaunchConfig settlementLimited = constrainBySettlement(stored);
        LaunchConfig executable = constrainByPaymentReadiness(settlementLimited);
        if (executable.stage() == stored.stage()) {
            return executable;
        }
        log.error(
                "저장된 공개 단계가 현재 결제·정산 실행 조건과 불일치함 stage={} settlementMode={} — stage={}로 fail-closed",
                stored.stage(),
                settlementMode.currentMode(),
                executable.stage());
        return executable;
    }

    @Override
    public LaunchConfig requested() {
        return stored();
    }

    @Override
    @Transactional
    public LaunchConfig changeStage(ChangeStageCommand command) {
        if (command.stage() == null) {
            throw new BadRequestException("LAUNCH_STAGE_REQUIRED", "변경할 공개 단계를 지정해야 합니다");
        }
        String reason = requireReason(command.reason());
        LaunchConfig before = stored();
        if (before.stage() == command.stage()) {
            // 같은 단계로의 재요청은 이력을 늘리지 않는다. 감사 로그가 의미 없는 줄로 덮이면
            // 정작 필요한 변경을 찾지 못한다.
            return before;
        }
        requireCompatibleSettlementMode(command.stage());
        LaunchConfig executableBefore = constrainBySettlement(before);
        // 결제가 새로 열리는 방향일 때만 검증한다. 단계를 내리는 조치(사고 대응)는 막지 않는다.
        if (opensPayments(executableBefore, command.stage())) {
            requirePaymentReadiness();
        }

        Instant now = Instant.now(clock);
        LaunchConfig after = before.withStage(command.stage(), now, command.actorId());
        repository.save(after);
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
        return after;
    }

    @Override
    @Transactional
    public LaunchConfig setFeatureOverride(SetFeatureOverrideCommand command) {
        if (command.feature() == null) {
            throw new BadRequestException("LAUNCH_FEATURE_REQUIRED", "변경할 기능을 지정해야 합니다");
        }
        String reason = requireReason(command.reason());
        LaunchConfig before = stored();
        boolean wasEnabled = before.isEnabled(command.feature());

        if (Boolean.TRUE.equals(command.enabled())) {
            if (command.feature() == LaunchFeature.PAYMENTS && !before.stage().atLeast(LaunchStage.TRADING)) {
                throw new ConflictException("LAUNCH_STAGE_REQUIRED_FOR_PAYMENTS", "결제는 Stage 2 이상에서만 열 수 있습니다");
            }
            if (command.feature() == LaunchFeature.PARTNER_PAYOUT) {
                if (before.stage() != LaunchStage.FULL || settlementMode.currentMode() != Mode.PROVIDER) {
                    throw new ConflictException(
                            "LAUNCH_PROVIDER_MODE_REQUIRED", "자동 지급은 Stage 3과 지급대행 모드가 모두 준비돼야 열 수 있습니다");
                }
                if (!before.isEnabled(LaunchFeature.PAYMENTS)) {
                    throw new ConflictException("LAUNCH_PAYMENTS_REQUIRED", "결제가 닫힌 상태에서는 자동 지급을 열 수 없습니다");
                }
            }
        }

        // 단계 기본으로는 닫혀 있는 결제를 override 로 여는 것도 결제를 여는 전이다.
        if (command.feature() == LaunchFeature.PAYMENTS && Boolean.TRUE.equals(command.enabled()) && !wasEnabled) {
            requirePaymentReadiness();
        }

        Instant now = Instant.now(clock);
        LaunchConfig after = before.withOverride(command.feature(), command.enabled(), now, command.actorId());
        repository.save(after);
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
    public List<LaunchConfigChange> history(int limit) {
        return history.findRecent(Math.max(1, Math.min(limit, MAX_HISTORY)));
    }

    /** 이 전이로 결제가 닫힌 상태에서 열린 상태가 되는가. */
    private static boolean opensPayments(LaunchConfig before, LaunchStage target) {
        if (before.isEnabled(LaunchFeature.PAYMENTS)) {
            return false;
        }
        LaunchConfig projected =
                new LaunchConfig(target, before.overrides(), before.updatedAt(), before.updatedBy(), before.version());
        return projected.isEnabled(LaunchFeature.PAYMENTS);
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

    private static LaunchStage executableStage(LaunchStage stored, Mode mode, boolean contractVerified) {
        if (!stored.atLeast(LaunchStage.TRADING)) {
            return stored;
        }
        if (mode == Mode.DISABLED || !contractVerified) {
            return LaunchStage.BROWSE_ONLY;
        }
        if (stored == LaunchStage.TRADING) {
            return mode == Mode.MANUAL ? stored : LaunchStage.BROWSE_ONLY;
        }
        return mode == Mode.PROVIDER ? stored : LaunchStage.TRADING;
    }

    private LaunchConfig constrainBySettlement(LaunchConfig stored) {
        LaunchStage executable =
                executableStage(stored.stage(), settlementMode.currentMode(), settlementMode.payoutContractVerified());
        return withExecutableStage(stored, executable);
    }

    /**
     * 저장된 희망 단계가 높아도 결제 설정이 사라지거나 깨지면 공개 실행 단계는 즉시 Stage 1로
     * 내려간다. 환경변수·정산 모드를 바꾼 뒤 과거 저장값이 자동으로 되살아나 결제가 열리는
     * 경로까지 막기 위한 런타임 fail-closed 방어다.
     */
    private LaunchConfig constrainByPaymentReadiness(LaunchConfig candidate) {
        if (!candidate.isEnabled(LaunchFeature.PAYMENTS)) {
            return candidate;
        }
        Snapshot snapshot = paymentReadiness.getPaymentReadiness();
        if (isReady(snapshot)) {
            return candidate;
        }
        return withExecutableStage(candidate, LaunchStage.BROWSE_ONLY);
    }

    private static LaunchConfig withExecutableStage(LaunchConfig source, LaunchStage executable) {
        if (source.stage() == executable) {
            return source;
        }
        return new LaunchConfig(
                executable, source.overrides(), source.updatedAt(), source.updatedBy(), source.version());
    }

    private LaunchConfig stored() {
        return repository.load().orElseGet(LaunchConfig::unset);
    }

    /**
     * 결제 설정이 실제로 준비됐는지 확인하고, 아니면 전이를 거부한다(fail-closed).
     *
     * <p>비밀값은 메시지에 담지 않는다 — 어떤 설정이 비었는지 이름만 알려준다.
     */
    private void requirePaymentReadiness() {
        Snapshot snapshot = paymentReadiness.getPaymentReadiness();
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

    /** 이력 표기: 최종 개방 여부와, 그것이 override 때문인지 단계 기본인지 함께 남긴다. */
    private static String describe(boolean enabled, Boolean override) {
        return (enabled ? "enabled" : "disabled") + (override == null ? "(stage default)" : "(override)");
    }
}
