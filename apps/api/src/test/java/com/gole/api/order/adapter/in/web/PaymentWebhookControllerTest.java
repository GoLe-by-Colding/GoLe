package com.gole.api.order.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.common.operations.OperationalEventPublisher;
import com.gole.api.order.application.port.in.ConfirmRefundUseCase;
import com.gole.api.order.application.port.in.PayOrderUseCase;
import com.gole.api.order.application.port.out.PaymentGatewayUnavailableException;
import com.gole.api.order.domain.exception.OrderNotFoundException;
import com.gole.api.order.domain.model.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PaymentWebhookControllerTest {

    private final PayOrderUseCase payments = mock(PayOrderUseCase.class);
    private final ConfirmRefundUseCase refunds = mock(ConfirmRefundUseCase.class);
    private final OperationalEventPublisher events = mock(OperationalEventPublisher.class);
    private final PortOneWebhookVerifier verifier = mock(PortOneWebhookVerifier.class);
    private final PaymentWebhookController controller =
            new PaymentWebhookController(payments, refunds, events, verifier, new ObjectMapper(), "store-1");

    @Test
    @DisplayName("PG 조회 일시 장애는 200으로 삼키지 않고 재시도 가능한 예외를 전파한다")
    void propagatesTemporaryVerificationFailure() {
        when(payments.pay("order-1"))
                .thenThrow(new PaymentGatewayUnavailableException("order-1", new IllegalStateException("timeout")));

        assertThatThrownBy(() -> controller.webhook(
                        "{\"type\":\"Transaction.Paid\",\"data\":{\"paymentId\":\"order-1\",\"storeId\":\"store-1\"}}",
                        "message-1",
                        "signature",
                        "timestamp"))
                .isInstanceOf(PaymentGatewayUnavailableException.class);

        verify(events, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("예기치 못한 저장소 장애는 ack하지 않고 PortOne 재시도를 위해 전파한다")
    void propagatesUnexpectedInfrastructureFailure() {
        when(payments.pay("order-db")).thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> controller.webhook(
                        "{\"type\":\"Transaction.Paid\",\"data\":{\"paymentId\":\"order-db\",\"storeId\":\"store-1\"}}",
                        "message-db",
                        "signature",
                        "timestamp"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        verify(events, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("존재하지 않는 주문처럼 재시도로 해결되지 않는 도메인 거절은 ack한다")
    void acknowledgesPermanentDomainRejection() {
        when(payments.pay("order-missing")).thenThrow(new OrderNotFoundException("order-missing"));

        controller.webhook(
                "{\"type\":\"Transaction.Paid\",\"data\":{\"paymentId\":\"order-missing\",\"storeId\":\"store-1\"}}",
                "message-missing",
                "signature",
                "timestamp");

        verify(events).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("정상 결제 웹훅은 paymentId로 서버 검증을 수행한다")
    void verifiesPaymentByPaymentId() {
        when(payments.pay("order-2")).thenReturn(OrderStatus.FUNDS_HELD);

        controller.webhook(
                "{\"type\":\"Transaction.Paid\",\"data\":{\"paymentId\":\"order-2\",\"storeId\":\"store-1\"}}",
                "message-2",
                "signature",
                "timestamp");

        verify(payments).pay("order-2");
    }

    @Test
    @DisplayName("최종 취소 웹훅은 PG 원장 재조회 기반의 환불 확정을 수행한다")
    void confirmsRefundByPaymentId() {
        controller.webhook(
                "{\"type\":\"Transaction.Cancelled\",\"data\":{\"paymentId\":\"order-3\",\"storeId\":\"store-1\"}}",
                "message-3",
                "signature",
                "timestamp");

        verify(refunds).confirmRefund("order-3");
        verify(payments, never()).pay("order-3");
    }

    @Test
    @DisplayName("알 수 없는 이벤트는 PG 조회 없이 ack한다")
    void ignoresUnsupportedEvent() {
        controller.webhook(
                "{\"type\":\"Unknown.Event\",\"data\":{\"paymentId\":\"order-4\",\"storeId\":\"store-1\"}}",
                "message-4",
                "signature",
                "timestamp");

        verify(payments, never()).pay("order-4");
        verify(refunds, never()).confirmRefund("order-4");
    }

    @Test
    @DisplayName("다른 상점의 웹훅은 주문에 반영하지 않고 ack한다")
    void ignoresWebhookFromDifferentStore() {
        controller.webhook(
                "{\"type\":\"Transaction.Paid\",\"data\":{\"paymentId\":\"order-5\",\"storeId\":\"store-other\"}}",
                "message-5",
                "signature",
                "timestamp");

        verify(payments, never()).pay("order-5");
        verify(refunds, never()).confirmRefund("order-5");
        verify(events).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("storeId가 누락된 웹훅은 주문에 반영하지 않고 ack한다")
    void ignoresWebhookWithoutStoreId() {
        controller.webhook(
                "{\"type\":\"Transaction.Cancelled\",\"data\":{\"paymentId\":\"order-6\"}}",
                "message-6",
                "signature",
                "timestamp");

        verify(payments, never()).pay("order-6");
        verify(refunds, never()).confirmRefund("order-6");
        verify(events).publish(org.mockito.ArgumentMatchers.any());
    }
}
