package com.gole.api.order.application.service.pipeline;

import com.gole.api.order.application.port.out.OrderRepositoryPort;
import com.gole.api.order.application.service.OrderPaymentTransitionService;
import com.gole.api.order.domain.model.Order;
import com.gole.api.order.domain.model.OrderStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 결제 미승인 만료. (R9: PAYMENT_PENDING 30분 → PAYMENT_FAILED + 매물 예약 해제)
 *
 * <p><b>스텁 결제 모드에서만 활성.</b> 실 PG(portone.enabled=true)에서는
 * {@code PaymentReconciliationScheduler}가 PG 원장을 대조한 뒤 결제 건이 없는 주문만
 * 만료한다 — 원장 확인 없는 시간 만료는 웹훅이 늦게 도착한 실결제를 죽일 수 있다.
 * 스텁 모드에는 원장이 없으므로 순수 타임아웃이 안전하고 또 유일한 방법이다.
 */
@Component
@ConditionalOnProperty(name = "portone.enabled", havingValue = "false", matchIfMissing = true)
public class PaymentPendingExpiryRule implements PipelineRule {

    private final OrderRepositoryPort orders;
    private final OrderPaymentTransitionService transitions;
    private final PipelineProperties properties;

    public PaymentPendingExpiryRule(
            OrderRepositoryPort orders, OrderPaymentTransitionService transitions, PipelineProperties properties) {
        this.orders = orders;
        this.transitions = transitions;
        this.properties = properties;
    }

    @Override
    public String name() {
        return "payment-pending-expiry";
    }

    @Override
    public List<String> candidates(Instant now) {
        return orders
                .findByStatusChangedBefore(OrderStatus.PAYMENT_PENDING, now.minus(properties.paymentPendingExpiry()))
                .stream()
                .map(Order::getId)
                .toList();
    }

    @Override
    public boolean apply(String orderId, Instant now) {
        // 트랜잭션 안에서 상태를 다시 읽으므로 결제 웹훅과의 경쟁도 안전하다.
        return transitions.expireMissingPayment(orderId, now) == OrderStatus.PAYMENT_FAILED;
    }
}
