package com.gole.api.order.adapter.in.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gole.api.account.adapter.in.web.UserAuthInterceptor;
import com.gole.api.order.application.port.in.CancelOrderUseCase;
import com.gole.api.order.application.port.in.CancelOrderUseCase.CancelOrderCommand;
import com.gole.api.order.application.port.in.GetOrderUseCase;
import com.gole.api.order.domain.model.Order;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class OrderCancellationControllerTest {
    @Test
    @DisplayName("취소 주체는 클라이언트 본문이 아닌 인증된 계정으로 전달한다")
    void cancel_usesAuthenticatedBuyerAndReturnsOrder() throws Exception {
        CancelOrderUseCase cancel = mock(CancelOrderUseCase.class);
        GetOrderUseCase orders = mock(GetOrderUseCase.class);
        Instant now = Instant.parse("2026-10-21T00:00:00Z");
        Order order = Order.place("order", "listing", "buyer", "seller", null, "new_sealed", 10000, now);
        order.cancel(now);
        when(orders.getById("order")).thenReturn(order);
        var mvc = MockMvcBuilders.standaloneSetup(new OrderCancellationController(cancel, orders))
                .build();
        mvc.perform(post("/api/v1/orders/order/cancellation")
                        .requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "buyer")
                        .contentType("application/json")
                        .content("{\"buyerId\":\"someone-else\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"));
        verify(cancel).cancel(new CancelOrderCommand("order", "buyer"));
    }
}
