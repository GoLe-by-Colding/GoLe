package com.gole.api.order.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 주문 애그리거트가 결제수단을 기록하는 규칙.
 *
 * <p>결제수단은 승인 원장에만 실려오는 사실이라 되찾을 곳이 없다. 그래서 "언제 기록되는지"와
 * "기록되지 않는 경우 무엇이 남는지"가 둘 다 명확해야 한다.
 */
class OrderPaymentMethodTest {

    private static final Instant NOW = Instant.parse("2026-08-17T05:00:00Z");

    private static Order pendingOrder() {
        return Order.place("order-1", "listing-1", "buyer-1", "seller-1", "10305", "new_sealed", 15_000, NOW);
    }

    @Test
    @DisplayName("결제 전 주문에는 결제수단이 없다")
    void hasNoPaymentMethodBeforePayment() {
        assertThat(pendingOrder().getPaymentMethod()).isNull();
    }

    @Test
    @DisplayName("자금 보유 전이에서 확인된 결제수단을 기록한다")
    void recordsPaymentMethodOnFundsHeld() {
        Order order = pendingOrder();
        PaymentMethod kakaoPay = new PaymentMethod(PaymentMethodType.EASY_PAY, "KAKAOPAY");

        order.confirmFundsHeld(NOW, kakaoPay);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FUNDS_HELD);
        assertThat(order.getPaymentMethod()).isEqualTo(kakaoPay);
    }

    /**
     * PG가 결제수단을 알려주지 않았다는 사실도 기록이다. null로 남기면 "결제 전"과
     * "결제했지만 수단 불명"이 구분되지 않는다.
     */
    @Test
    @DisplayName("결제수단을 알 수 없는 승인은 UNKNOWN으로 남긴다")
    void recordsUnknownWhenGatewayDidNotReportMethod() {
        Order order = pendingOrder();

        order.confirmFundsHeld(NOW, null);

        assertThat(order.getPaymentMethod()).isEqualTo(PaymentMethod.UNKNOWN);
    }

    @Test
    @DisplayName("실패한 결제는 결제수단을 남기지 않는다")
    void keepsPaymentMethodAbsentOnFailedPayment() {
        Order order = pendingOrder();

        order.failPayment(NOW);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(order.getPaymentMethod()).isNull();
    }

    /** 환불은 "무엇으로 결제했는가"를 지우지 않는다 — 환불 응대와 정산 대사가 그 사실 위에서 이뤄진다. */
    @Test
    @DisplayName("환불 후에도 결제수단 기록은 남는다")
    void preservesPaymentMethodAfterRefund() {
        Order order = pendingOrder();
        PaymentMethod kakaoPay = new PaymentMethod(PaymentMethodType.EASY_PAY, "KAKAOPAY");
        order.confirmFundsHeld(NOW, kakaoPay);

        order.requestRefund(NOW.plusSeconds(30));
        order.refund(NOW.plusSeconds(60));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(order.getPaymentMethod()).isEqualTo(kakaoPay);
    }
}
