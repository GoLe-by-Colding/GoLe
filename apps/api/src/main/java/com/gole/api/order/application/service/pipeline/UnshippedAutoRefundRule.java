package com.gole.api.order.application.service.pipeline;

import com.gole.api.order.application.port.in.RefundOrderUseCase;
import com.gole.api.order.application.port.out.OrderEventNotifierPort;
import com.gole.api.order.application.port.out.OrderRepositoryPort;
import com.gole.api.order.domain.model.Order;
import com.gole.api.order.domain.model.OrderStatus;
import com.gole.api.shipping.application.port.in.GetShipmentUseCase;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 미발송 자동 전액 환불. (R9: FUNDS_HELD + 운송장 미등록 7일 → REFUNDED + 매물 복구)
 *
 * <p>기존 {@link RefundOrderUseCase}를 그대로 호출한다 — 매물 선점 해제·운영 이벤트가
 * 이미 그 안에 붙어 있다(R7.3). 환불이므로 수수료는 없다(R5.5).
 */
@Component
public class UnshippedAutoRefundRule implements PipelineRule {

    private final OrderRepositoryPort orders;
    private final GetShipmentUseCase shipments;
    private final RefundOrderUseCase refundOrder;
    private final OrderEventNotifierPort notifier;
    private final PipelineProperties properties;

    public UnshippedAutoRefundRule(
            OrderRepositoryPort orders,
            GetShipmentUseCase shipments,
            RefundOrderUseCase refundOrder,
            OrderEventNotifierPort notifier,
            PipelineProperties properties) {
        this.orders = orders;
        this.shipments = shipments;
        this.refundOrder = refundOrder;
        this.notifier = notifier;
        this.properties = properties;
    }

    @Override
    public String name() {
        return "unshipped-auto-refund";
    }

    @Override
    public List<String> candidates(Instant now) {
        return orders
                .findByStatusChangedBefore(OrderStatus.FUNDS_HELD, now.minus(properties.unshippedRefundAfter()))
                .stream()
                .map(Order::getId)
                .toList();
    }

    @Override
    public boolean apply(String orderId, Instant now) {
        if (shipments.getByOrderId(orderId).isPresent()) {
            return false; // 발송됨 — 환불 대상 아님
        }
        Order order = orders.findById(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.FUNDS_HELD) {
            return false;
        }
        refundOrder.refund(orderId);
        notifier.autoRefundedForNoShipment(order.getBuyerId(), order.getSellerId(), orderId);
        return true;
    }
}
