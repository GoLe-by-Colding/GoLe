package com.gole.api.order.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gole.api.common.operations.OperationalEvent;
import com.gole.api.common.operations.OperationalEvent.Category;
import com.gole.api.common.operations.OperationalEventPublisher;
import com.gole.api.common.web.GlobalExceptionHandler;
import com.gole.api.order.domain.exception.PaymentGatewayUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class PaymentGatewayExceptionHandlerTest {

    private final OperationalEventPublisher events = mock(OperationalEventPublisher.class);
    private final PaymentGatewayExceptionHandler handler = new PaymentGatewayExceptionHandler(events);

    @Test
    @DisplayName("PG 일시 장애는 503 이고 결제 운영 알림을 올린다")
    void paymentGatewayOutageIsServiceUnavailable() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/payments/webhook");

        var response = handler.handlePaymentGatewayUnavailable(
                new PaymentGatewayUnavailableException("order-1", new IllegalStateException("timeout")), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().code()).isEqualTo("PAYMENT_GATEWAY_UNAVAILABLE");
        ArgumentCaptor<OperationalEvent> event = ArgumentCaptor.forClass(OperationalEvent.class);
        verify(events).publish(event.capture());
        assertThat(event.getValue().category()).isEqualTo(Category.PAYMENT);
    }

    @Test
    @DisplayName("공통 처리기의 catch-all 보다 먼저 고르도록 우선순위가 가장 높다")
    void handlerRunsBeforeTheGlobalCatchAll() {
        assertThat(PaymentGatewayExceptionHandler.class
                        .getAnnotation(Order.class)
                        .value())
                .isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }

    @Test
    @DisplayName("공통 처리기와 함께 등록돼도 PG 장애는 catch-all 500 이 아니라 503 이다")
    void outageBeatsGlobalCatchAllAtHttpBoundary() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler(events), handler)
                .build();

        mvc.perform(get("/throw-payment-outage"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PAYMENT_GATEWAY_UNAVAILABLE"));
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/throw-payment-outage")
        String fail() {
            throw new PaymentGatewayUnavailableException("order-1", new IllegalStateException("timeout"));
        }
    }
}
