package com.gole.api.launch.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.exception.ConflictException;
import com.gole.api.launch.application.port.in.ManageLaunchConfigUseCase.ChangeStageCommand;
import com.gole.api.launch.application.port.in.ManageLaunchConfigUseCase.SetFeatureOverrideCommand;
import com.gole.api.launch.application.port.out.LaunchConfigHistoryPort;
import com.gole.api.launch.application.port.out.LaunchConfigRepositoryPort;
import com.gole.api.launch.application.port.out.LaunchSettlementModePort;
import com.gole.api.launch.application.port.out.LaunchSettlementModePort.Mode;
import com.gole.api.launch.domain.model.LaunchConfig;
import com.gole.api.launch.domain.model.LaunchConfigChange;
import com.gole.api.launch.domain.model.LaunchFeature;
import com.gole.api.launch.domain.model.LaunchStage;
import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase;
import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase.ChannelType;
import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase.ConfigurationIssue;
import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase.Problem;
import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase.Snapshot;
import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase.State;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.OptimisticLockingFailureException;

class LaunchConfigServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-29T00:00:00Z");

    private final LaunchConfigRepositoryPort repository = mock(LaunchConfigRepositoryPort.class);
    private final LaunchConfigHistoryPort history = mock(LaunchConfigHistoryPort.class);
    private final GetPaymentReadinessUseCase readiness = mock(GetPaymentReadinessUseCase.class);
    private final LaunchSettlementModePort settlementMode = mock(LaunchSettlementModePort.class);
    private final LaunchConfigService service =
            new LaunchConfigService(repository, history, readiness, settlementMode, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void defaultToManualSettlement() {
        when(settlementMode.currentMode()).thenReturn(Mode.MANUAL);
        when(settlementMode.payoutContractVerified()).thenReturn(true);
        when(readiness.getPaymentReadiness()).thenReturn(ready());
    }

    private static Snapshot ready() {
        return new Snapshot(true, true, State.READY, ChannelType.LIVE, List.of("KAKAOPAY"), "KRW", List.of());
    }

    private static Snapshot misconfigured() {
        return new Snapshot(
                true,
                false,
                State.MISCONFIGURED,
                ChannelType.UNKNOWN,
                List.of(),
                "KRW",
                List.of(new ConfigurationIssue("portone.api-secret", Problem.MISSING)));
    }

    private void stored(LaunchStage stage) {
        when(repository.load()).thenReturn(Optional.of(new LaunchConfig(stage, Map.of(), null, "admin-0")));
    }

    @Test
    @DisplayName("설정이 없으면 돈을 만지지 않는 Stage 1을 안전 기본값으로 쓴다")
    void currentFallsBackToBrowseOnly() {
        when(repository.load()).thenReturn(Optional.empty());

        assertThat(service.current().stage()).isEqualTo(LaunchStage.BROWSE_ONLY);
        assertThat(service.current().platformHandlesMoney()).isFalse();
    }

    @Test
    @DisplayName("관리자 요청값은 정산 모드로 낮춘 실행값과 별도로 보존한다")
    void requestedKeepsStoredStage() {
        stored(LaunchStage.FULL);
        when(settlementMode.currentMode()).thenReturn(Mode.DISABLED);

        assertThat(service.current().stage()).isEqualTo(LaunchStage.BROWSE_ONLY);
        assertThat(service.requested().stage()).isEqualTo(LaunchStage.FULL);
    }

    @Test
    @DisplayName("저장 단계와 실제 정산 모드가 어긋나면 실행 가능한 단계로 fail-closed 한다")
    void currentUsesStageBySettlementModeSafetyMatrix() {
        assertExecutableStage(LaunchStage.PREPARING, Mode.DISABLED, LaunchStage.PREPARING);
        assertExecutableStage(LaunchStage.PREPARING, Mode.MANUAL, LaunchStage.PREPARING);
        assertExecutableStage(LaunchStage.PREPARING, Mode.PROVIDER, LaunchStage.PREPARING);
        assertExecutableStage(LaunchStage.BROWSE_ONLY, Mode.DISABLED, LaunchStage.BROWSE_ONLY);
        assertExecutableStage(LaunchStage.BROWSE_ONLY, Mode.MANUAL, LaunchStage.BROWSE_ONLY);
        assertExecutableStage(LaunchStage.BROWSE_ONLY, Mode.PROVIDER, LaunchStage.BROWSE_ONLY);
        assertExecutableStage(LaunchStage.TRADING, Mode.DISABLED, LaunchStage.BROWSE_ONLY);
        assertExecutableStage(LaunchStage.TRADING, Mode.MANUAL, LaunchStage.TRADING);
        assertExecutableStage(LaunchStage.TRADING, Mode.PROVIDER, LaunchStage.BROWSE_ONLY);
        assertExecutableStage(LaunchStage.FULL, Mode.DISABLED, LaunchStage.BROWSE_ONLY);
        assertExecutableStage(LaunchStage.FULL, Mode.MANUAL, LaunchStage.TRADING);
        assertExecutableStage(LaunchStage.FULL, Mode.PROVIDER, LaunchStage.FULL);
    }

    @Test
    @DisplayName("지급대행 계약 확인 전에는 저장값이 높아도 Stage 1로 잠근다")
    void unverifiedContractClampsMoneyStages() {
        stored(LaunchStage.FULL);
        when(settlementMode.currentMode()).thenReturn(Mode.PROVIDER);
        when(settlementMode.payoutContractVerified()).thenReturn(false);

        assertThat(service.current().stage()).isEqualTo(LaunchStage.BROWSE_ONLY);
    }

    @Test
    @DisplayName("결제 설정이 런타임에서 깨지면 저장 단계가 높아도 Stage 1로 잠근다")
    void misconfiguredPaymentClampsMoneyStages() {
        stored(LaunchStage.FULL);
        when(settlementMode.currentMode()).thenReturn(Mode.PROVIDER);
        when(readiness.getPaymentReadiness()).thenReturn(misconfigured());

        assertThat(service.current().stage()).isEqualTo(LaunchStage.BROWSE_ONLY);
        assertThat(service.requested().stage()).isEqualTo(LaunchStage.FULL);
    }

    @Test
    @DisplayName("정산 모드 변경으로 실행 단계가 낮아졌다면 상향 전이에 결제 준비 검증을 다시 한다")
    void modeChangeCannotBypassPaymentReadiness() {
        stored(LaunchStage.TRADING);
        when(settlementMode.currentMode()).thenReturn(Mode.PROVIDER);
        when(readiness.getPaymentReadiness()).thenReturn(misconfigured());

        assertThatThrownBy(() -> service.changeStage(
                        new ChangeStageCommand(LaunchStage.FULL, "지급대행 전환", "admin-1", "a@gole.local")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("결제 설정이 준비되지 않아");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("지급대행 계약 확인 전에는 Stage 2 전환을 거부한다")
    void unverifiedContractRejectsTradingStage() {
        stored(LaunchStage.BROWSE_ONLY);
        when(settlementMode.payoutContractVerified()).thenReturn(false);
        when(readiness.getPaymentReadiness()).thenReturn(ready());

        assertThatThrownBy(() -> service.changeStage(
                        new ChangeStageCommand(LaunchStage.TRADING, "결제 오픈", "admin-1", "a@gole.local")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("계약 확인");

        verify(repository, never()).save(any());
    }

    private void assertExecutableStage(LaunchStage storedStage, Mode mode, LaunchStage expectedStage) {
        stored(storedStage);
        when(settlementMode.currentMode()).thenReturn(mode);

        assertThat(service.current().stage())
                .as("저장 단계 %s, 정산 모드 %s", storedStage, mode)
                .isEqualTo(expectedStage);
    }

    @Test
    @DisplayName("사유 없는 단계 변경은 거부한다")
    void reasonIsRequired() {
        stored(LaunchStage.PREPARING);

        assertThatThrownBy(() -> service.changeStage(
                        new ChangeStageCommand(LaunchStage.BROWSE_ONLY, "  ", "admin-1", "a@gole.local")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("변경 사유");

        verify(repository, never()).save(any());
        verify(history, never()).append(any());
    }

    @Test
    @DisplayName("낙관적 잠금으로 설정 저장이 실패하면 변경 이력을 남기지 않는다")
    void optimisticSaveFailureDoesNotAppendHistory() {
        stored(LaunchStage.BROWSE_ONLY);
        doThrow(new OptimisticLockingFailureException("동시 변경")).when(repository).save(any(LaunchConfig.class));

        assertThatThrownBy(() -> service.changeStage(
                        new ChangeStageCommand(LaunchStage.PREPARING, "점검 전환", "admin-1", "a@gole.local")))
                .isInstanceOf(OptimisticLockingFailureException.class);

        verify(history, never()).append(any());
    }

    @Test
    @DisplayName("결제가 열리는 전이는 PortOne 설정이 준비되지 않으면 거부한다(fail-closed)")
    void openingPaymentsRequiresPaymentReadiness() {
        stored(LaunchStage.BROWSE_ONLY);
        when(readiness.getPaymentReadiness()).thenReturn(misconfigured());

        assertThatThrownBy(() -> service.changeStage(
                        new ChangeStageCommand(LaunchStage.TRADING, "결제 오픈", "admin-1", "a@gole.local")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("결제 설정이 준비되지 않아");

        // 거부된 전이는 저장도 이력도 남기지 않는다.
        verify(repository, never()).save(any());
        verify(history, never()).append(any());
    }

    @Test
    @DisplayName("결제 준비 상태를 확인할 수 없어도 전이를 거부한다")
    void nullReadinessSnapshotIsTreatedAsNotReady() {
        stored(LaunchStage.BROWSE_ONLY);
        when(readiness.getPaymentReadiness()).thenReturn(null);

        assertThatThrownBy(() -> service.changeStage(
                        new ChangeStageCommand(LaunchStage.TRADING, "결제 오픈", "admin-1", "a@gole.local")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("결제 준비가 끝났으면 단계를 올리고 전/후를 이력에 남긴다")
    void openingPaymentsSucceedsWhenReady() {
        stored(LaunchStage.BROWSE_ONLY);
        when(readiness.getPaymentReadiness()).thenReturn(ready());

        LaunchConfig updated =
                service.changeStage(new ChangeStageCommand(LaunchStage.TRADING, "PG 계약 완료", "admin-1", "a@gole.local"));

        assertThat(updated.stage()).isEqualTo(LaunchStage.TRADING);
        assertThat(updated.updatedAt()).isEqualTo(NOW);
        ArgumentCaptor<LaunchConfigChange> change = ArgumentCaptor.forClass(LaunchConfigChange.class);
        verify(history).append(change.capture());
        assertThat(change.getValue().before()).isEqualTo("1");
        assertThat(change.getValue().after()).isEqualTo("2");
        assertThat(change.getValue().reason()).isEqualTo("PG 계약 완료");
        assertThat(change.getValue().actorId()).isEqualTo("admin-1");
    }

    @Test
    @DisplayName("Stage 2는 MANUAL, Stage 3은 PROVIDER 정산 모드에서만 열 수 있다")
    void stageTransitionRequiresMatchingSettlementMode() {
        stored(LaunchStage.BROWSE_ONLY);
        when(readiness.getPaymentReadiness()).thenReturn(ready());

        for (Mode incompatible : new Mode[] {Mode.DISABLED, Mode.PROVIDER}) {
            when(settlementMode.currentMode()).thenReturn(incompatible);
            assertThatThrownBy(() -> service.changeStage(
                            new ChangeStageCommand(LaunchStage.TRADING, "Stage 2 전환", "admin-1", "a@gole.local")))
                    .as("Stage 2, 정산 모드 %s", incompatible)
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("수동 정산 모드");
        }

        for (Mode incompatible : new Mode[] {Mode.DISABLED, Mode.MANUAL}) {
            when(settlementMode.currentMode()).thenReturn(incompatible);
            assertThatThrownBy(() -> service.changeStage(
                            new ChangeStageCommand(LaunchStage.FULL, "Stage 3 전환", "admin-1", "a@gole.local")))
                    .as("Stage 3, 정산 모드 %s", incompatible)
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("지급대행 모드");
        }

        when(settlementMode.currentMode()).thenReturn(Mode.PROVIDER);
        assertThat(service.changeStage(
                                new ChangeStageCommand(LaunchStage.FULL, "지급대행 계약 완료", "admin-1", "a@gole.local"))
                        .stage())
                .isEqualTo(LaunchStage.FULL);
    }

    @Test
    @DisplayName("단계를 내리는 조치는 결제 준비 상태와 무관하게 허용한다")
    void loweringStageIsNeverBlockedByReadiness() {
        stored(LaunchStage.FULL);

        LaunchConfig updated = service.changeStage(
                new ChangeStageCommand(LaunchStage.PREPARING, "장애 대응 긴급 차단", "admin-1", "a@gole.local"));

        assertThat(updated.stage()).isEqualTo(LaunchStage.PREPARING);
        verify(readiness, never()).getPaymentReadiness();
        verify(repository).save(any());
    }

    @Test
    @DisplayName("같은 단계로의 재요청은 이력을 남기지 않는다")
    void sameStageIsNoop() {
        stored(LaunchStage.TRADING);

        service.changeStage(new ChangeStageCommand(LaunchStage.TRADING, "확인차", "admin-1", "a@gole.local"));

        verify(repository, never()).save(any());
        verify(history, never()).append(any());
    }

    @Test
    @DisplayName("Stage 0·1에서는 override 로도 결제를 열 수 없다")
    void lowerStageCannotOpenPaymentsByOverride() {
        for (LaunchStage directStage : new LaunchStage[] {LaunchStage.PREPARING, LaunchStage.BROWSE_ONLY}) {
            stored(directStage);

            assertThatThrownBy(() -> service.setFeatureOverride(new SetFeatureOverrideCommand(
                            LaunchFeature.PAYMENTS, true, "선오픈", "admin-1", "a@gole.local")))
                    .as("단계 %s", directStage)
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Stage 2 이상");
        }

        verify(repository, never()).save(any());
        verify(readiness, never()).getPaymentReadiness();
    }

    @Test
    @DisplayName("Stage 2에서 override 로 결제를 다시 여는 것도 준비 검증을 거친다")
    void enablingPaymentsByOverrideAtTradingAlsoRequiresReadiness() {
        when(repository.load())
                .thenReturn(Optional.of(
                        new LaunchConfig(LaunchStage.TRADING, Map.of(LaunchFeature.PAYMENTS, false), null, "admin-0")));
        when(readiness.getPaymentReadiness()).thenReturn(misconfigured());

        assertThatThrownBy(() -> service.setFeatureOverride(
                        new SetFeatureOverrideCommand(LaunchFeature.PAYMENTS, true, "재오픈", "admin-1", "a@gole.local")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("결제 설정이 준비되지 않아");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("결제를 닫는 override 는 준비 검증 없이 즉시 허용한다")
    void closingPaymentsNeedsNoReadinessCheck() {
        stored(LaunchStage.FULL);

        LaunchConfig updated = service.setFeatureOverride(
                new SetFeatureOverrideCommand(LaunchFeature.PAYMENTS, false, "결제사 장애", "admin-1", "a@gole.local"));

        assertThat(updated.isEnabled(LaunchFeature.PAYMENTS)).isFalse();
        verify(readiness, never()).getPaymentReadiness();
    }

    @Test
    @DisplayName("이력 조회 한도는 상한으로 잘린다")
    void historyLimitIsClamped() {
        when(history.findRecent(200)).thenReturn(List.of());

        service.history(9_999);

        verify(history).findRecent(200);
    }
}
