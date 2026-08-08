package com.gole.api.order.application.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.common.operations.OperationalEventPublisher;
import com.gole.api.order.application.port.out.OrderRepositoryPort;
import com.gole.api.order.application.port.out.PaymentGatewayPort;
import com.gole.api.order.application.port.out.PaymentGatewayPort.PaymentVerificationResult;
import com.gole.api.order.domain.model.Order;
import com.gole.api.order.domain.model.OrderStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaymentReconciliationSchedulerTest {

    @Test
    void reconcilesOnlyRepositorySelectedStalePendingOrders() {
        Instant now = Instant.parse("2026-08-09T05:00:00Z");
        Order order = Order.place(
                "order-1", "listing-1", "buyer-1", "seller-1", "10307", "new_sealed", 280_000, now.minusSeconds(900));
        OrderRepositoryPort orders = mock(OrderRepositoryPort.class);
        PaymentGatewayPort gateway = mock(PaymentGatewayPort.class);
        OrderPaymentTransitionService transitions = mock(OrderPaymentTransitionService.class);
        OperationalEventPublisher events = mock(OperationalEventPublisher.class);
        when(orders.findPaymentPendingCreatedBefore(now.minusSeconds(600))).thenReturn(List.of(order));
        when(gateway.verifyPayment("order-1", 280_000)).thenReturn(PaymentVerificationResult.PAID);
        when(transitions.applyPaymentVerification("order-1", PaymentVerificationResult.PAID, now))
                .thenReturn(OrderStatus.FUNDS_HELD);
        PaymentReconciliationScheduler scheduler = new PaymentReconciliationScheduler(
                orders, gateway, transitions, events, Clock.fixed(now, ZoneOffset.UTC), Duration.ofMinutes(10));

        scheduler.reconcileStalePayments();

        verify(orders).findPaymentPendingCreatedBefore(now.minusSeconds(600));
        verify(gateway).verifyPayment("order-1", 280_000);
        verify(transitions).applyPaymentVerification("order-1", PaymentVerificationResult.PAID, now);
        verify(events)
                .publish(org.mockito.ArgumentMatchers.argThat(
                        event -> event.fields().get("상태 변경").equals("1")
                                && event.fields().get("재시도 필요").equals("0")));
    }
}
