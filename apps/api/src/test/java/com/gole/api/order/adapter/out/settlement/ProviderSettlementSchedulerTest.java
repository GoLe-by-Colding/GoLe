package com.gole.api.order.adapter.out.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gole.api.common.operations.OperationalEvent;
import com.gole.api.common.operations.OperationalEventPublisher;
import com.gole.api.launch.application.port.in.GetLaunchConfigUseCase;
import com.gole.api.launch.domain.model.LaunchFeature;
import com.gole.api.order.application.port.out.AutomaticSettlementPort;
import com.gole.api.order.application.port.out.AutomaticSettlementPort.Candidate;
import com.gole.api.order.application.port.out.OrderRepositoryPort;
import com.gole.api.order.domain.model.Order;
import com.gole.api.order.domain.model.OrderStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 지급대행 스케줄러가 선점된 원장을 정확히 한 번 외부 지급으로 넘기는지 검증한다. */
class ProviderSettlementSchedulerTest {

    private static final Instant BEFORE_HOLDBACK = Instant.parse("2026-08-12T00:00:00Z");
    private static final Instant AFTER_HOLDBACK = Instant.parse("2026-08-16T00:00:00Z");
    private static final Duration HOLDBACK = Duration.ofDays(3);
    private static final Duration CLAIM_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration RETRY_AFTER = Duration.ofMinutes(5);

    private final AutomaticSettlementPort settlements = mock(AutomaticSettlementPort.class);
    private final OrderRepositoryPort orders = mock(OrderRepositoryPort.class);
    private final SettlementExecutor executor = mock(SettlementExecutor.class);
    private final GetLaunchConfigUseCase launchConfig = mock(GetLaunchConfigUseCase.class);
    private final OperationalEventPublisher operationalEvents = mock(OperationalEventPublisher.class);
    private final SettlementProperties properties = new SettlementProperties();
    private final MutableClock clock = new MutableClock(BEFORE_HOLDBACK);

    private ProviderSettlementScheduler scheduler;

    @BeforeEach
    void setUp() {
        properties.setMode(SettlementProperties.Mode.PROVIDER);
        properties.setPayoutContractVerified(true);
        properties.setPayoutHoldback(HOLDBACK);
        properties.setProviderClaimTimeout(CLAIM_TIMEOUT);
        properties.setProviderRetryAfter(RETRY_AFTER);
        properties.setProviderBatchSize(5);
        properties.setProviderMaxAttempts(5);
        scheduler = new ProviderSettlementScheduler(
                settlements, orders, executor, properties, launchConfig, operationalEvents, clock);
        when(launchConfig.isEnabled(LaunchFeature.PARTNER_PAYOUT)).thenReturn(true);
        when(executor.canPayAutomatically()).thenReturn(true);
    }

    @Test
    void doesNotPayBeforeHoldbackAndPaysExactlyOnceAfterIt() {
        Candidate candidate = new Candidate("order-1", "seller-1", 95_000, "attempt-after", 1);
        Order completed = orderWithStatus(OrderStatus.COMPLETED);
        when(settlements.claimNext(eq(BEFORE_HOLDBACK), eq(HOLDBACK), eq(CLAIM_TIMEOUT), anyString()))
                .thenReturn(Optional.empty());
        when(settlements.claimNext(eq(AFTER_HOLDBACK), eq(HOLDBACK), eq(CLAIM_TIMEOUT), anyString()))
                .thenReturn(Optional.of(candidate))
                .thenReturn(Optional.empty());
        when(orders.findById("order-1")).thenReturn(Optional.of(completed));
        when(executor.payIfAutomatic("order-1", "seller-1", 95_000)).thenReturn(Optional.of("provider-ref-1"));

        assertThat(scheduler.processDue()).isZero();
        verifyNoInteractions(orders);
        verify(executor, never()).payIfAutomatic(anyString(), anyString(), eq(95_000L));

        clock.set(AFTER_HOLDBACK);
        assertThat(scheduler.processDue()).isEqualTo(1);

        verify(settlements).blockExhaustedClaims(AFTER_HOLDBACK, CLAIM_TIMEOUT, 5);
        verify(executor).payIfAutomatic("order-1", "seller-1", 95_000);
        verify(settlements).markPaid("order-1", "attempt-after", "provider-ref-1", AFTER_HOLDBACK);
    }

    @Test
    void repeatedSchedulerRunsDoNotPayALedgerAlreadyConsumedByTheFirstClaim() {
        Candidate candidate = new Candidate("order-1", "seller-1", 95_000, "attempt-1", 1);
        Order completed = orderWithStatus(OrderStatus.COMPLETED);
        when(settlements.claimNext(eq(BEFORE_HOLDBACK), eq(HOLDBACK), eq(CLAIM_TIMEOUT), anyString()))
                .thenReturn(Optional.of(candidate))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());
        when(orders.findById("order-1")).thenReturn(Optional.of(completed));
        when(executor.payIfAutomatic("order-1", "seller-1", 95_000)).thenReturn(Optional.of("provider-ref-1"));

        assertThat(scheduler.processDue()).isEqualTo(1);
        assertThat(scheduler.processDue()).isZero();

        verify(executor, times(1)).payIfAutomatic("order-1", "seller-1", 95_000);
        verify(settlements, times(1)).markPaid("order-1", "attempt-1", "provider-ref-1", BEFORE_HOLDBACK);
    }

    @Test
    void recordsProviderFailureForRetryWithoutMarkingPaid() {
        Candidate candidate = new Candidate("order-1", "seller-1", 95_000, "attempt-1", 1);
        Order completed = orderWithStatus(OrderStatus.COMPLETED);
        when(settlements.claimNext(eq(BEFORE_HOLDBACK), eq(HOLDBACK), eq(CLAIM_TIMEOUT), anyString()))
                .thenReturn(Optional.of(candidate))
                .thenReturn(Optional.empty());
        when(orders.findById("order-1")).thenReturn(Optional.of(completed));
        when(executor.payIfAutomatic("order-1", "seller-1", 95_000))
                .thenThrow(new IllegalStateException("provider timeout"));

        assertThat(scheduler.processDue()).isEqualTo(1);

        verify(settlements).markFailed("order-1", "attempt-1", "provider timeout", BEFORE_HOLDBACK, RETRY_AFTER);
        verify(settlements, never()).markPaid(eq("order-1"), eq("attempt-1"), anyString(), eq(BEFORE_HOLDBACK));
        verify(operationalEvents).publish(any(OperationalEvent.class));
    }

    @Test
    void blocksPayoutAfterTheConfiguredFinalProviderFailure() {
        Candidate candidate = new Candidate("order-1", "seller-1", 95_000, "attempt-5", 5);
        Order completed = orderWithStatus(OrderStatus.COMPLETED);
        when(settlements.claimNext(eq(BEFORE_HOLDBACK), eq(HOLDBACK), eq(CLAIM_TIMEOUT), anyString()))
                .thenReturn(Optional.of(candidate))
                .thenReturn(Optional.empty());
        when(orders.findById("order-1")).thenReturn(Optional.of(completed));
        when(executor.payIfAutomatic("order-1", "seller-1", 95_000))
                .thenThrow(new IllegalStateException("invalid partner"));

        assertThat(scheduler.processDue()).isEqualTo(1);

        verify(settlements)
                .markBlocked(
                        eq("order-1"),
                        eq("attempt-5"),
                        org.mockito.ArgumentMatchers.contains("5/5"),
                        eq(BEFORE_HOLDBACK));
        verify(settlements, never())
                .markFailed(eq("order-1"), eq("attempt-5"), anyString(), eq(BEFORE_HOLDBACK), eq(RETRY_AFTER));
    }

    @Test
    void blocksDamagedLedgerWhenAuthoritativeOrderIsMissing() {
        Candidate candidate = new Candidate("missing-order", "seller-1", 95_000, "attempt-1", 1);
        when(settlements.claimNext(eq(BEFORE_HOLDBACK), eq(HOLDBACK), eq(CLAIM_TIMEOUT), anyString()))
                .thenReturn(Optional.of(candidate))
                .thenReturn(Optional.empty());
        when(orders.findById("missing-order")).thenReturn(Optional.empty());

        assertThat(scheduler.processDue()).isEqualTo(1);

        verify(settlements).markBlocked("missing-order", "attempt-1", "권위 주문이 지급 가능 상태가 아님: missing", BEFORE_HOLDBACK);
        verify(executor, never()).payIfAutomatic(anyString(), anyString(), eq(95_000L));
    }

    @Test
    void blocksLedgerWhenOrderLeftCompletedState() {
        Candidate candidate = new Candidate("order-1", "seller-1", 95_000, "attempt-1", 1);
        Order refunded = orderWithStatus(OrderStatus.REFUNDED);
        when(settlements.claimNext(eq(BEFORE_HOLDBACK), eq(HOLDBACK), eq(CLAIM_TIMEOUT), anyString()))
                .thenReturn(Optional.of(candidate))
                .thenReturn(Optional.empty());
        when(orders.findById("order-1")).thenReturn(Optional.of(refunded));

        assertThat(scheduler.processDue()).isEqualTo(1);

        verify(settlements).markBlocked("order-1", "attempt-1", "권위 주문이 지급 가능 상태가 아님: REFUNDED", BEFORE_HOLDBACK);
        verify(executor, never()).payIfAutomatic(anyString(), anyString(), eq(95_000L));
    }

    @Test
    void disabledExecutorDoesNotEvenClaimASettlement() {
        when(executor.canPayAutomatically()).thenReturn(false);

        assertThat(scheduler.processDue()).isZero();

        verifyNoInteractions(settlements, orders);
    }

    @Test
    void emergencyLaunchOverrideStopsAutomaticPayoutBeforeClaimingAnyLedger() {
        when(launchConfig.isEnabled(LaunchFeature.PARTNER_PAYOUT)).thenReturn(false);

        assertThat(scheduler.processDue()).isZero();

        verifyNoInteractions(settlements, orders);
        verify(executor, never()).canPayAutomatically();
    }

    @Test
    void successfulProviderPaymentWithLedgerWriteFailureKeepsClaimForIdempotentRecovery() {
        Candidate candidate = new Candidate("order-1", "seller-1", 95_000, "attempt-1", 1);
        Order completed = orderWithStatus(OrderStatus.COMPLETED);
        when(settlements.claimNext(eq(BEFORE_HOLDBACK), eq(HOLDBACK), eq(CLAIM_TIMEOUT), anyString()))
                .thenReturn(Optional.of(candidate))
                .thenReturn(Optional.empty());
        when(orders.findById("order-1")).thenReturn(Optional.of(completed));
        when(executor.payIfAutomatic("order-1", "seller-1", 95_000)).thenReturn(Optional.of("provider-ref-1"));
        org.mockito.Mockito.doThrow(new IllegalStateException("mongo unavailable"))
                .when(settlements)
                .markPaid("order-1", "attempt-1", "provider-ref-1", BEFORE_HOLDBACK);

        assertThat(scheduler.processDue()).isEqualTo(1);

        verify(settlements, never())
                .markFailed(eq("order-1"), eq("attempt-1"), anyString(), eq(BEFORE_HOLDBACK), eq(RETRY_AFTER));
        verify(operationalEvents).publish(any(OperationalEvent.class));
    }

    private static Order orderWithStatus(OrderStatus status) {
        Order order = mock(Order.class);
        when(order.getStatus()).thenReturn(status);
        return order;
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
