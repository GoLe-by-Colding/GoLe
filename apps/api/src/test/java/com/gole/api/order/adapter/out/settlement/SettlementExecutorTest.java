package com.gole.api.order.adapter.out.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.order.adapter.out.settlement.SettlementProperties.Mode;
import com.gole.api.order.application.port.out.SettlementExecutionPort;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 정산 모드가 설정에만 있고 런타임을 안 바꾸면 오설정이 조용히 넘어간다. 이 테스트는
 * 모드가 실제로 지급 동작과 부팅 가능 여부를 가르는지 고정한다.
 */
class SettlementExecutorTest {

    private final SettlementExecutionPort provider = mock(SettlementExecutionPort.class);

    private static SettlementProperties props(Mode mode) {
        SettlementProperties properties = new SettlementProperties();
        properties.setMode(mode);
        properties.setPayoutContractVerified(true);
        return properties;
    }

    @Test
    void safeDefaultDisablesSettlementUntilOperatorChoosesAMode() {
        assertThat(new SettlementProperties().getMode()).isEqualTo(Mode.DISABLED);
        assertThat(new SettlementProperties().isPayoutContractVerified()).isFalse();
    }

    @Test
    void providerModeWithoutAnAdapterRefusesToBoot() {
        SettlementExecutor executor = new SettlementExecutor(props(Mode.PROVIDER), Optional.empty());

        assertThatThrownBy(executor::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SettlementExecutionPort 구현체가 없습니다");
    }

    @Test
    void nonProviderModeWithAnAdapterRefusesToBoot() {
        SettlementExecutor executor = new SettlementExecutor(props(Mode.MANUAL), Optional.of(provider));

        assertThatThrownBy(executor::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PROVIDER로 올리세요");
    }

    @Test
    void negativePayoutHoldbackRefusesToBoot() {
        SettlementProperties properties = props(Mode.MANUAL);
        properties.setPayoutHoldback(Duration.ofSeconds(-1));
        SettlementExecutor executor = new SettlementExecutor(properties, Optional.empty());

        assertThatThrownBy(executor::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("payout-holdback");
    }

    @Test
    void zeroProviderMaxAttemptsRefusesToBootEvenOutsideSpringBinding() {
        SettlementProperties properties = props(Mode.PROVIDER);
        properties.setProviderMaxAttempts(0);
        SettlementExecutor executor = new SettlementExecutor(properties, Optional.of(provider));

        assertThatThrownBy(executor::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("max-attempts");
    }

    @Test
    void manualModeNeverPaysAutomatically() {
        SettlementExecutor executor = new SettlementExecutor(props(Mode.MANUAL), Optional.empty());
        executor.afterPropertiesSet();

        assertThat(executor.canPayAutomatically()).isFalse();
        assertThat(executor.payIfAutomatic("order-1", "seller-1", 95_000)).isEmpty();
        verify(provider, never()).execute("order-1", "seller-1", 95_000);
    }

    /** DISABLED 기본값은 기존 원장을 보존하되 판매자 지급을 시도하지 않아야 한다. */
    @Test
    void disabledModeNeverPaysAutomatically() {
        SettlementExecutor executor = new SettlementExecutor(props(Mode.DISABLED), Optional.empty());
        executor.afterPropertiesSet();

        assertThat(executor.canPayAutomatically()).isFalse();
        assertThat(executor.payIfAutomatic("order-1", "seller-1", 95_000)).isEmpty();
        verify(provider, never()).execute("order-1", "seller-1", 95_000);
    }

    @Test
    void providerModeDelegatesToTheAdapterAndReturnsItsReference() {
        when(provider.execute("order-1", "seller-1", 95_000)).thenReturn("payout-tx-9");
        SettlementExecutor executor = new SettlementExecutor(props(Mode.PROVIDER), Optional.of(provider));
        executor.afterPropertiesSet();

        assertThat(executor.canPayAutomatically()).isTrue();
        assertThat(executor.payIfAutomatic("order-1", "seller-1", 95_000)).contains("payout-tx-9");
    }

    @Test
    void providerModeCannotPayBeforeContractIsVerified() {
        SettlementProperties properties = props(Mode.PROVIDER);
        properties.setPayoutContractVerified(false);
        SettlementExecutor executor = new SettlementExecutor(properties, Optional.of(provider));
        executor.afterPropertiesSet();

        assertThat(executor.canPayAutomatically()).isFalse();
        assertThat(executor.payIfAutomatic("order-1", "seller-1", 95_000)).isEmpty();
        verify(provider, never()).execute("order-1", "seller-1", 95_000);
    }
}
