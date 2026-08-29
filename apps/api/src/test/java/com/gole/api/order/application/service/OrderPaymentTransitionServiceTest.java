package com.gole.api.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.order.application.port.out.ListingReservationPort;
import com.gole.api.order.application.port.out.OrderRepositoryPort;
import com.gole.api.order.application.port.out.PaymentGatewayPort.PaymentVerification;
import com.gole.api.order.application.port.out.PaymentGatewayPort.PaymentVerificationResult;
import com.gole.api.order.domain.model.Order;
import com.gole.api.order.domain.model.OrderStatus;
import com.gole.api.order.domain.model.PaymentEvidenceKind;
import com.gole.api.order.domain.model.PaymentMethod;
import com.gole.api.order.domain.model.PaymentMethodType;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OrderPaymentTransitionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-09T05:00:00Z");

    @Test
    void normalVerificationDoesNotFailOrReleaseMissingPayment() {
        Order order = pendingOrder();
        OrderRepositoryPort orders = mock(OrderRepositoryPort.class);
        ListingReservationPort listings = mock(ListingReservationPort.class);
        when(orders.findById("order-1")).thenReturn(Optional.of(order));
        OrderPaymentTransitionService service = new OrderPaymentTransitionService(orders, listings);

        OrderStatus status = service.applyPaymentVerification(
                "order-1", PaymentVerification.of(PaymentVerificationResult.NOT_FOUND), NOW);

        assertThat(status).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        verify(orders, never()).save(order);
        verify(listings, never()).release("listing-1");
    }

    /** PG가 알려준 결제수단이 전이를 타고 주문에 실제로 기록되는지 — 포트와 도메인 사이가 끊기기 쉬운 곳이다. */
    @Test
    void paidVerificationRecordsReportedPaymentMethod() {
        Order order = pendingOrder();
        OrderRepositoryPort orders = mock(OrderRepositoryPort.class);
        ListingReservationPort listings = mock(ListingReservationPort.class);
        when(orders.findById("order-1")).thenReturn(Optional.of(order));
        when(orders.save(order)).thenReturn(order);
        OrderPaymentTransitionService service = new OrderPaymentTransitionService(orders, listings);
        PaymentMethod kakaoPay = new PaymentMethod(PaymentMethodType.EASY_PAY, "KAKAOPAY");

        OrderStatus status = service.applyPaymentVerification(
                "order-1", PaymentVerification.paid(kakaoPay, PaymentEvidenceKind.TEST), NOW);

        assertThat(status).isEqualTo(OrderStatus.FUNDS_HELD);
        assertThat(order.getPaymentMethod()).isEqualTo(kakaoPay);
        assertThat(order.getPaymentEvidenceKind()).isEqualTo(PaymentEvidenceKind.TEST);
    }

    @Test
    void ttlExpiryFailsMissingPaymentAndReleasesReservation() {
        Order order = pendingOrder();
        OrderRepositoryPort orders = mock(OrderRepositoryPort.class);
        ListingReservationPort listings = mock(ListingReservationPort.class);
        when(orders.findById("order-1")).thenReturn(Optional.of(order));
        when(orders.save(order)).thenReturn(order);
        OrderPaymentTransitionService service = new OrderPaymentTransitionService(orders, listings);

        OrderStatus status = service.expireMissingPayment("order-1", NOW);

        assertThat(status).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        verify(listings).release("listing-1");
        verify(orders).save(order);
    }

    @Test
    void ttlExpiryRechecksLatestStateBeforeReleasingReservation() {
        Order order = pendingOrder();
        order.confirmFundsHeld(NOW.minusSeconds(1));
        OrderRepositoryPort orders = mock(OrderRepositoryPort.class);
        ListingReservationPort listings = mock(ListingReservationPort.class);
        when(orders.findById("order-1")).thenReturn(Optional.of(order));
        OrderPaymentTransitionService service = new OrderPaymentTransitionService(orders, listings);

        OrderStatus status = service.expireMissingPayment("order-1", NOW);

        assertThat(status).isEqualTo(OrderStatus.FUNDS_HELD);
        verify(listings, never()).release("listing-1");
        verify(orders, never()).save(order);
    }

    private static Order pendingOrder() {
        return Order.place(
                "order-1", "listing-1", "buyer-1", "seller-1", "10307", "new_sealed", 280_000, NOW.minusSeconds(900));
    }
}
