package com.gole.api.order.application.service.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gole.api.order.application.port.out.OrderRepositoryPort;
import com.gole.api.order.application.service.OrderPaymentTransitionService;
import com.gole.api.order.domain.model.OrderStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PaymentPendingExpiryRuleTest {

    private static final Instant NOW = Instant.parse("2026-09-04T10:00:00Z");

    private final OrderRepositoryPort orders = mock(OrderRepositoryPort.class);
    private final OrderPaymentTransitionService transitions = mock(OrderPaymentTransitionService.class);
    private final PipelineProperties properties = new PipelineProperties();

    @Test
    void productionPaymentOffNeverExpiresOrdersWithoutPgLedgerCheck() {
        var rule = new PaymentPendingExpiryRule(orders, transitions, properties, "production");

        assertThat(rule.candidates(NOW)).isEmpty();
        assertThat(rule.apply("order-1", NOW)).isFalse();

        verifyNoInteractions(orders, transitions);
    }

    @Test
    void unknownEnvironmentAlsoFailsClosed() {
        var rule = new PaymentPendingExpiryRule(orders, transitions, properties, "production-typo");

        assertThat(rule.candidates(NOW)).isEmpty();
        assertThat(rule.apply("order-1", NOW)).isFalse();

        verifyNoInteractions(orders, transitions);
    }

    @Test
    void localStubStillAppliesTheExistingTimeoutRule() {
        var rule = new PaymentPendingExpiryRule(orders, transitions, properties, "local");
        when(transitions.expireMissingPayment("order-1", NOW)).thenReturn(OrderStatus.PAYMENT_FAILED);

        assertThat(rule.apply("order-1", NOW)).isTrue();
    }
}
