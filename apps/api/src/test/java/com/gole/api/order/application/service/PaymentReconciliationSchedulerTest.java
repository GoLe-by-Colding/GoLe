package com.gole.api.order.application.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gole.api.common.operations.OperationalEventPublisher;
import com.gole.api.order.application.port.in.RefundOrderUseCase;
import com.gole.api.order.application.port.out.OrderRepositoryPort;
import com.gole.api.order.application.port.out.PaymentGatewayPort;
import com.gole.api.order.application.port.out.PaymentGatewayPort.PaymentVerification;
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
        RefundOrderUseCase refunds = mock(RefundOrderUseCase.class);
        OperationalEventPublisher events = mock(OperationalEventPublisher.class);
        when(orders.findPaymentPendingCreatedBefore(now.minusSeconds(600))).thenReturn(List.of(order));
        when(gateway.verifyPayment("order-1", 280_000))
                .thenReturn(PaymentVerification.of(PaymentVerificationResult.PAID));
        when(transitions.applyPaymentVerification(
                        "order-1", PaymentVerification.of(PaymentVerificationResult.PAID), now))
                .thenReturn(OrderStatus.FUNDS_HELD);
        PaymentReconciliationScheduler scheduler = new PaymentReconciliationScheduler(
                orders,
                gateway,
                transitions,
                refunds,
                events,
                Clock.fixed(now, ZoneOffset.UTC),
                Duration.ofMinutes(10));

        scheduler.reconcileStalePayments();

        verify(orders).findPaymentPendingCreatedBefore(now.minusSeconds(600));
        verify(gateway).verifyPayment("order-1", 280_000);
        verify(transitions)
                .applyPaymentVerification("order-1", PaymentVerification.of(PaymentVerificationResult.PAID), now);
        verify(events)
                .publish(org.mockito.ArgumentMatchers.argThat(
                        event -> event.fields().get("상태 변경").equals("1")
                                && event.fields().get("결제 미시작 만료").equals("0")
                                && event.fields().get("재시도 필요").equals("0")));
    }

    @Test
    void retriesStaleRefundPendingOrdersThroughTheIdempotentRefundFlow() {
        Instant now = Instant.parse("2026-08-09T05:00:00Z");
        Order order = Order.place(
                "order-refund",
                "listing-1",
                "buyer-1",
                "seller-1",
                "10307",
                "new_sealed",
                280_000,
                now.minusSeconds(1_200));
        order.confirmFundsHeld(now.minusSeconds(1_100), null);
        order.requestRefund(now.minusSeconds(900));
        OrderRepositoryPort orders = mock(OrderRepositoryPort.class);
        PaymentGatewayPort gateway = mock(PaymentGatewayPort.class);
        OrderPaymentTransitionService transitions = mock(OrderPaymentTransitionService.class);
        RefundOrderUseCase refunds = mock(RefundOrderUseCase.class);
        OperationalEventPublisher events = mock(OperationalEventPublisher.class);
        when(orders.findPaymentPendingCreatedBefore(now.minusSeconds(600))).thenReturn(List.of());
        when(orders.findByStatusAfterId(OrderStatus.REFUND_PENDING, null)).thenReturn(List.of(order));
        when(orders.findById("order-refund")).thenReturn(java.util.Optional.of(order));
        PaymentReconciliationScheduler scheduler = new PaymentReconciliationScheduler(
                orders,
                gateway,
                transitions,
                refunds,
                events,
                Clock.fixed(now, ZoneOffset.UTC),
                Duration.ofMinutes(10));

        scheduler.reconcileStalePayments();

        verify(refunds).refund("order-refund");
        verify(events)
                .publish(org.mockito.ArgumentMatchers.argThat(
                        event -> event.fields().get("환불 재조정").equals("1")
                                && event.fields().get("환불 완료").equals("0")
                                && event.fields().get("재시도 필요").equals("0")));
    }

    @Test
    void advancesRefundCursorSoARepeatedlyFailingFirstPageCannotStarveLaterOrders() {
        Instant now = Instant.parse("2026-08-09T05:00:00Z");
        Order first = refundPendingOrder("order-a", now);
        Order second = refundPendingOrder("order-b", now);
        OrderRepositoryPort orders = mock(OrderRepositoryPort.class);
        PaymentGatewayPort gateway = mock(PaymentGatewayPort.class);
        OrderPaymentTransitionService transitions = mock(OrderPaymentTransitionService.class);
        RefundOrderUseCase refunds = mock(RefundOrderUseCase.class);
        OperationalEventPublisher events = mock(OperationalEventPublisher.class);
        when(orders.findPaymentPendingCreatedBefore(now.minusSeconds(600))).thenReturn(List.of());
        when(orders.findByStatusAfterId(OrderStatus.REFUND_PENDING, null)).thenReturn(List.of(first));
        when(orders.findByStatusAfterId(OrderStatus.REFUND_PENDING, "order-a")).thenReturn(List.of(second));
        org.mockito.Mockito.doThrow(new IllegalStateException("temporary failure"))
                .when(refunds)
                .refund("order-a");
        when(orders.findById("order-b")).thenReturn(java.util.Optional.of(second));
        PaymentReconciliationScheduler scheduler = scheduler(orders, gateway, transitions, refunds, events, now);

        scheduler.reconcileStalePayments();
        scheduler.reconcileStalePayments();

        verify(refunds).refund("order-a");
        verify(refunds).refund("order-b");
        verify(orders).findByStatusAfterId(OrderStatus.REFUND_PENDING, "order-a");
    }

    @Test
    void expiresAgedReservationOnlyWhenPortOneHasNoPaymentRecord() {
        Instant now = Instant.parse("2026-08-09T05:00:00Z");
        Order order = pendingOrder(now);
        OrderRepositoryPort orders = mock(OrderRepositoryPort.class);
        PaymentGatewayPort gateway = mock(PaymentGatewayPort.class);
        OrderPaymentTransitionService transitions = mock(OrderPaymentTransitionService.class);
        RefundOrderUseCase refunds = mock(RefundOrderUseCase.class);
        OperationalEventPublisher events = mock(OperationalEventPublisher.class);
        when(orders.findPaymentPendingCreatedBefore(now.minusSeconds(600))).thenReturn(List.of(order));
        when(gateway.verifyPayment("order-1", 280_000))
                .thenReturn(PaymentVerification.of(PaymentVerificationResult.NOT_FOUND));
        when(transitions.expireMissingPayment("order-1", now)).thenReturn(OrderStatus.PAYMENT_FAILED);
        PaymentReconciliationScheduler scheduler = scheduler(orders, gateway, transitions, refunds, events, now);

        scheduler.reconcileStalePayments();

        verify(transitions).expireMissingPayment("order-1", now);
        verify(transitions, never())
                .applyPaymentVerification("order-1", PaymentVerification.of(PaymentVerificationResult.NOT_FOUND), now);
        verify(events)
                .publish(org.mockito.ArgumentMatchers.argThat(
                        event -> event.fields().get("상태 변경").equals("1")
                                && event.fields().get("결제 미시작 만료").equals("1")
                                && event.fields().get("재시도 필요").equals("0")));
    }

    @Test
    void neverExpiresAgedReadyOrPendingPayment() {
        Instant now = Instant.parse("2026-08-09T05:00:00Z");
        Order order = pendingOrder(now);
        OrderRepositoryPort orders = mock(OrderRepositoryPort.class);
        PaymentGatewayPort gateway = mock(PaymentGatewayPort.class);
        OrderPaymentTransitionService transitions = mock(OrderPaymentTransitionService.class);
        RefundOrderUseCase refunds = mock(RefundOrderUseCase.class);
        OperationalEventPublisher events = mock(OperationalEventPublisher.class);
        when(orders.findPaymentPendingCreatedBefore(now.minusSeconds(600))).thenReturn(List.of(order));
        when(gateway.verifyPayment("order-1", 280_000))
                .thenReturn(PaymentVerification.of(PaymentVerificationResult.PENDING));
        when(transitions.applyPaymentVerification(
                        "order-1", PaymentVerification.of(PaymentVerificationResult.PENDING), now))
                .thenReturn(OrderStatus.PAYMENT_PENDING);
        PaymentReconciliationScheduler scheduler = scheduler(orders, gateway, transitions, refunds, events, now);

        scheduler.reconcileStalePayments();

        verify(transitions)
                .applyPaymentVerification("order-1", PaymentVerification.of(PaymentVerificationResult.PENDING), now);
        verify(transitions, never()).expireMissingPayment("order-1", now);
        verifyNoInteractions(events);
    }

    private static Order pendingOrder(Instant now) {
        return Order.place(
                "order-1", "listing-1", "buyer-1", "seller-1", "10307", "new_sealed", 280_000, now.minusSeconds(900));
    }

    private static Order refundPendingOrder(String id, Instant now) {
        Order order = Order.place(
                id, "listing-1", "buyer-1", "seller-1", "10307", "new_sealed", 280_000, now.minusSeconds(1_200));
        order.confirmFundsHeld(now.minusSeconds(1_100), null);
        order.requestRefund(now.minusSeconds(900));
        return order;
    }

    private static PaymentReconciliationScheduler scheduler(
            OrderRepositoryPort orders,
            PaymentGatewayPort gateway,
            OrderPaymentTransitionService transitions,
            RefundOrderUseCase refunds,
            OperationalEventPublisher events,
            Instant now) {
        return new PaymentReconciliationScheduler(
                orders,
                gateway,
                transitions,
                refunds,
                events,
                Clock.fixed(now, ZoneOffset.UTC),
                Duration.ofMinutes(10));
    }
}
