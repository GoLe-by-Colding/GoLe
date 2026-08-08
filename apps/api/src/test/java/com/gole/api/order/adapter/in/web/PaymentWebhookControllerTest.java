package com.gole.api.order.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.common.operations.OperationalEventPublisher;
import com.gole.api.order.application.port.in.PayOrderUseCase;
import com.gole.api.order.application.port.out.PaymentGatewayUnavailableException;
import com.gole.api.order.domain.model.OrderStatus;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaymentWebhookControllerTest {

    private final PayOrderUseCase payments = mock(PayOrderUseCase.class);
    private final OperationalEventPublisher events = mock(OperationalEventPublisher.class);
    private final PaymentWebhookController controller = new PaymentWebhookController(payments, events);

    @Test
    @DisplayName("PG 조회 일시 장애는 200으로 삼키지 않고 재시도 가능한 예외를 전파한다")
    void propagatesTemporaryVerificationFailure() {
        when(payments.pay("order-1"))
                .thenThrow(new PaymentGatewayUnavailableException("order-1", new IllegalStateException("timeout")));

        assertThatThrownBy(() -> controller.webhook(Map.of("data", Map.of("paymentId", "order-1"))))
                .isInstanceOf(PaymentGatewayUnavailableException.class);

        verify(events, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("정상 결제 웹훅은 paymentId로 서버 검증을 수행한다")
    void verifiesPaymentByPaymentId() {
        when(payments.pay("order-2")).thenReturn(OrderStatus.FUNDS_HELD);

        controller.webhook(Map.of("data", Map.of("paymentId", "order-2")));

        verify(payments).pay("order-2");
    }
}
