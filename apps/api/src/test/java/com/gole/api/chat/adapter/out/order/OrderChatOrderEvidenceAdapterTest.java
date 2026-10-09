package com.gole.api.chat.adapter.out.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gole.api.order.application.port.in.GetOrderUseCase;
import com.gole.api.order.domain.model.Order;
import com.gole.api.order.domain.model.OrderStatus;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderChatOrderEvidenceAdapterTest {

    private final GetOrderUseCase orders = mock(GetOrderUseCase.class);
    private final OrderChatOrderEvidenceAdapter adapter = new OrderChatOrderEvidenceAdapter(orders);

    @Test
    @DisplayName("구매·판매 주문 중 하나라도 종결 전이면 증거가 남아 있다")
    void hasUnsettledOrder_whenAnyOrderIsNotTerminal() {
        Order completed = order(OrderStatus.COMPLETED);
        Order disputed = order(OrderStatus.DISPUTED);
        when(orders.getByBuyerId("user-1")).thenReturn(List.of(completed));
        when(orders.getBySellerId("user-1")).thenReturn(List.of(disputed));

        assertThat(adapter.hasUnsettledOrder("user-1")).isTrue();
    }

    @Test
    @DisplayName("모든 주문이 결제 실패·완료·환불로 끝났으면 남은 증거가 없다")
    void noUnsettledOrder_whenAllOrdersAreTerminal() {
        Order failed = order(OrderStatus.PAYMENT_FAILED);
        Order refunded = order(OrderStatus.REFUNDED);
        Order completed = order(OrderStatus.COMPLETED);
        when(orders.getByBuyerId("user-1")).thenReturn(List.of(failed, refunded));
        when(orders.getBySellerId("user-1")).thenReturn(List.of(completed));

        assertThat(adapter.hasUnsettledOrder("user-1")).isFalse();
    }

    private static Order order(OrderStatus status) {
        Order order = mock(Order.class);
        when(order.getStatus()).thenReturn(status);
        return order;
    }
}
