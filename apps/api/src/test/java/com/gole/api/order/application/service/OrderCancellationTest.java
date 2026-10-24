package com.gole.api.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.common.operations.OperationalEventPublisher;
import com.gole.api.order.application.port.in.CancelOrderUseCase.CancelOrderCommand;
import com.gole.api.order.application.port.out.*;
import com.gole.api.order.application.port.out.PaymentGatewayPort.PaymentVerification;
import com.gole.api.order.application.port.out.PaymentGatewayPort.PaymentVerificationResult;
import com.gole.api.order.domain.exception.OrderStateException;
import com.gole.api.order.domain.exception.PaymentGatewayUnavailableException;
import com.gole.api.order.domain.model.Order;
import com.gole.api.order.domain.model.OrderStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class OrderCancellationTest {
    private static final Instant NOW = Instant.parse("2026-10-21T00:00:00Z");
    private final OrderRepositoryPort orders = mock(OrderRepositoryPort.class);
    private final ListingReservationPort listings = mock(ListingReservationPort.class);
    private final PaymentGatewayPort gateway = mock(PaymentGatewayPort.class);
    private final SettlementPort settlements = mock(SettlementPort.class);
    private final Order order = Order.place("order", "listing", "buyer", "seller", null, "new_sealed", 10000, NOW);
    private OrderService service;
    private OrderPaymentTransitionService transitions;

    @BeforeEach
    void setUp() {
        when(orders.findById("order")).thenReturn(Optional.of(order));
        when(orders.save(any())).thenAnswer(call -> call.getArgument(0));
        transitions = new OrderPaymentTransitionService(orders, listings);
        service = new OrderService(
                orders,
                listings,
                gateway,
                settlements,
                mock(ExecutedPriceRecorderPort.class),
                mock(SellerNotifierPort.class),
                mock(OrderEventNotifierPort.class),
                mock(OrderIdGeneratorPort.class),
                Clock.fixed(NOW, ZoneOffset.UTC),
                transitions,
                mock(OperationalEventPublisher.class),
                mock(AcceptedOfferPort.class));
    }

    @ParameterizedTest
    @EnumSource(
            value = PaymentVerificationResult.class,
            names = {"NOT_FOUND", "FAILED"})
    @DisplayName("미승인 원장을 확인한 취소는 예약을 한 번만 해제하고 중복 취소는 멱등이다")
    void cancel_unpaid_isIdempotent(PaymentVerificationResult result) {
        when(gateway.verifyPaymentForCancellation("order", 10000)).thenReturn(PaymentVerification.of(result));
        service.cancel(new CancelOrderCommand("order", "buyer"));
        service.cancel(new CancelOrderCommand("order", "buyer"));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(listings, times(1)).release("listing");
        verify(gateway, times(1)).verifyPaymentForCancellation("order", 10000);
        verifyNoInteractions(settlements);
    }

    @ParameterizedTest
    @ValueSource(strings = {"seller", "other-buyer"})
    @DisplayName("판매자와 타 구매자는 PG 조회 전에 거부한다")
    void cancel_otherParty_isRejected(String actor) {
        assertThatThrownBy(() -> service.cancel(new CancelOrderCommand("order", actor)))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(gateway, listings);
    }

    @ParameterizedTest
    @EnumSource(
            value = PaymentVerificationResult.class,
            names = {"PAID", "PENDING", "REVIEW_REQUIRED"})
    @DisplayName("승인·진행·검토 원장은 취소 성공으로 처리하지 않으며 예약을 유지한다")
    void cancel_ambiguousOrPaid_preservesReservation(PaymentVerificationResult result) {
        when(gateway.verifyPaymentForCancellation("order", 10000)).thenReturn(PaymentVerification.of(result));
        assertThatThrownBy(() -> service.cancel(new CancelOrderCommand("order", "buyer")))
                .isInstanceOf(ConflictException.class);
        assertThat(order.getStatus())
                .isEqualTo(
                        switch (result) {
                            case PAID -> OrderStatus.FUNDS_HELD;
                            case REVIEW_REQUIRED -> OrderStatus.PAYMENT_REVIEW;
                            default -> OrderStatus.PAYMENT_PENDING;
                        });
        verifyNoInteractions(listings, settlements);
    }

    @Test
    @DisplayName("PG 조회 장애면 주문과 예약을 보존한다")
    void cancel_gatewayFailure_doesNotRelease() {
        when(gateway.verifyPaymentForCancellation("order", 10000))
                .thenThrow(new PaymentGatewayUnavailableException("order", new IllegalStateException("offline")));
        assertThatThrownBy(() -> service.cancel(new CancelOrderCommand("order", "buyer")))
                .isInstanceOf(PaymentGatewayUnavailableException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        verifyNoInteractions(listings);
    }

    @Test
    @DisplayName("원장 조회 중 승인 웹훅이 먼저 커밋되면 취소가 예약을 풀지 않는다")
    void cancel_whenApprovalWins_doesNotRelease() {
        when(gateway.verifyPaymentForCancellation("order", 10000)).thenAnswer(call -> {
            order.confirmFundsHeld(NOW);
            return PaymentVerification.of(PaymentVerificationResult.NOT_FOUND);
        });
        assertThatThrownBy(() -> service.cancel(new CancelOrderCommand("order", "buyer")))
                .isInstanceOf(OrderStateException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FUNDS_HELD);
        verifyNoInteractions(listings);
    }

    @Test
    @DisplayName("취소 뒤 늦은 승인은 환불하며 새 구매자의 예약을 다시 해제하지 않는다")
    void pay_afterCancellation_refundsWithoutReleasingAgain() {
        when(gateway.verifyPaymentForCancellation("order", 10000))
                .thenReturn(PaymentVerification.of(PaymentVerificationResult.NOT_FOUND));
        service.cancel(new CancelOrderCommand("order", "buyer"));
        clearInvocations(listings);
        when(gateway.verifyPayment("order", 10000)).thenReturn(PaymentVerification.of(PaymentVerificationResult.PAID));
        when(gateway.refund("order", 10000)).thenReturn(PaymentGatewayPort.RefundResult.SUCCEEDED);

        assertThat(service.pay("order")).isEqualTo(OrderStatus.REFUNDED);
        assertThat(service.pay("order")).isEqualTo(OrderStatus.REFUNDED);
        verify(gateway, times(1)).refund("order", 10000);
        verifyNoInteractions(listings, settlements);
    }

    @Test
    @DisplayName("취소 뒤 늦은 승인의 환불 장애도 자금 상태를 잃지 않는다")
    void pay_lateRefundFailure_staysPending() {
        order.cancel(NOW);
        when(gateway.verifyPayment("order", 10000)).thenReturn(PaymentVerification.of(PaymentVerificationResult.PAID));
        when(gateway.refund("order", 10000))
                .thenThrow(new PaymentGatewayUnavailableException("order", new IllegalStateException("offline")));
        assertThatThrownBy(() -> service.pay("order")).isInstanceOf(PaymentGatewayUnavailableException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUND_PENDING);
        assertThat(order.wasCancelledBeforePayment()).isTrue();
        verifyNoInteractions(listings, settlements);
    }

    @Test
    @DisplayName("취소 뒤 검증 불일치는 취소 주문을 승인하거나 환불하지 않고 운영 검토로 남긴다")
    void pay_lateReview_doesNotApprove() {
        order.cancel(NOW);
        assertThatThrownBy(() -> transitions.applyPaymentVerification(
                        "order", PaymentVerification.of(PaymentVerificationResult.REVIEW_REQUIRED), NOW))
                .isInstanceOf(PaymentReviewRequiredException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verifyNoInteractions(listings, settlements);
    }
}
