package com.gole.api.launch.application.service;

import com.gole.api.launch.application.port.out.LaunchConfigHistoryPort;
import com.gole.api.launch.application.port.out.LaunchConfigRepositoryPort;
import com.gole.api.launch.application.port.out.LaunchSettlementModePort;
import com.gole.api.launch.application.port.out.LaunchSettlementModePort.Mode;
import com.gole.api.launch.domain.model.LaunchConfig;
import com.gole.api.launch.domain.model.LaunchConfigChange;
import com.gole.api.launch.domain.model.LaunchFeature;
import com.gole.api.launch.domain.model.LaunchStage;
import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase;
import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase.Snapshot;
import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase.State;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 안전 단계 판정·저장·구조화 이력을 한 개의 독립 트랜잭션으로 확정한다. */
@Component
public class LaunchConfigSafetyClampWriter {

    static final String SAFETY_ACTOR_ID = "system:launch-safety-clamp";
    static final String SAFETY_ACTOR_EMAIL = "system@gole.local";
    static final String SAFETY_REASON = "결제·정산 실행 조건 실패로 Stage 1에 안전 잠금";

    private static final Logger log = LoggerFactory.getLogger(LaunchConfigSafetyClampWriter.class);

    private final LaunchConfigRepositoryPort repository;
    private final LaunchConfigHistoryPort history;
    private final GetPaymentReadinessUseCase paymentReadiness;
    private final LaunchSettlementModePort settlementMode;
    private final Clock clock;

    public LaunchConfigSafetyClampWriter(
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

    /**
     * 안전 단계를 별도 트랜잭션으로 확정한다.
     *
     * <p>관리자 상향 요청이 뒤에서 준비 상태 오류로 거부되더라도 이 래치와 이력은 롤백되지
     * 않는다. 환경이 회복돼도 저장 단계는 Stage 1에 남아 관리자 명시 전환을 요구한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LaunchConfig enforceOnce() {
        LaunchConfig before = repository.load().orElseGet(LaunchConfig::unset);
        LaunchStage safeStage = executableStage(before);
        if (safeStage == before.stage()) {
            return before;
        }

        Instant now = Instant.now(clock);
        LaunchConfig candidate = before.withStage(safeStage, now, SAFETY_ACTOR_ID);
        LaunchConfig persisted = repository.save(candidate);
        history.append(new LaunchConfigChange(
                UUID.randomUUID().toString(),
                LaunchConfigChange.Type.STAGE,
                "stage",
                Integer.toString(before.stage().level()),
                Integer.toString(safeStage.level()),
                SAFETY_REASON,
                SAFETY_ACTOR_ID,
                SAFETY_ACTOR_EMAIL,
                now));
        log.error(
                "저장된 공개 단계를 실행 조건에 맞춰 영구 잠금함 stage={} settlementMode={} -> stage={}; 관리자 명시 재개 필요",
                before.stage(),
                settlementMode.currentMode(),
                safeStage);
        return persisted == null ? candidate : persisted;
    }

    private LaunchStage executableStage(LaunchConfig stored) {
        if (!stored.stage().atLeast(LaunchStage.TRADING)) {
            return stored.stage();
        }

        if (!stored.hasRequiredReadiness(stored.stage())) {
            return LaunchStage.BROWSE_ONLY;
        }

        Mode mode = settlementMode.currentMode();
        if (!settlementMode.payoutContractVerified()) {
            return LaunchStage.BROWSE_ONLY;
        }
        if (stored.stage() == LaunchStage.TRADING && mode != Mode.MANUAL) {
            return LaunchStage.BROWSE_ONLY;
        }
        if (stored.stage() == LaunchStage.FULL && mode != Mode.PROVIDER) {
            return LaunchStage.BROWSE_ONLY;
        }
        if (!stored.isEnabled(LaunchFeature.PAYMENTS)) {
            return stored.stage();
        }

        Snapshot snapshot;
        try {
            snapshot = paymentReadiness.getPaymentReadiness();
        } catch (RuntimeException readinessFailure) {
            log.error("결제 준비 상태 조회 실패 — Stage 1로 fail-closed", readinessFailure);
            return LaunchStage.BROWSE_ONLY;
        }
        return isReady(snapshot) ? stored.stage() : LaunchStage.BROWSE_ONLY;
    }

    private static boolean isReady(Snapshot snapshot) {
        return snapshot != null && snapshot.ready() && snapshot.state() == State.READY;
    }
}
