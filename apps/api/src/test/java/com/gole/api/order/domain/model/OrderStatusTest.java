package com.gole.api.order.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderStatusTest {

    @Test
    @DisplayName("취소 주문은 지연 승인 회수 전 문의 증거를 파기할 종결 상태로 간주하지 않는다")
    void isTerminal_cancelledStillPreservesPaymentEvidence() {
        assertThat(OrderStatus.CANCELLED.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("결제 실패·완료·환불 완료만 종결 상태다")
    void isTerminal_onlyForFinalStates() {
        EnumSet<OrderStatus> terminal = EnumSet.noneOf(OrderStatus.class);
        for (OrderStatus status : OrderStatus.values()) {
            if (status.isTerminal()) {
                terminal.add(status);
            }
        }

        assertThat(terminal)
                .containsExactlyInAnyOrder(OrderStatus.PAYMENT_FAILED, OrderStatus.COMPLETED, OrderStatus.REFUNDED);
    }
}
