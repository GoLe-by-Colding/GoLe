package com.gole.api.order.application.service.pipeline;

import com.gole.api.order.application.port.out.OrderEventNotifierPort;
import com.gole.api.order.application.port.out.OrderRepositoryPort;
import com.gole.api.order.application.port.out.PipelineMarkerPort;
import com.gole.api.order.domain.model.Order;
import com.gole.api.order.domain.model.OrderStatus;
import com.gole.api.shipping.application.port.in.GetShipmentUseCase;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 발송 독촉. (R9: FUNDS_HELD + 운송장 미등록 3일 → 판매자 알림)
 *
 * <p>상태 전이가 없는 액션이라 마커로 1회성을 보장한다 — 없으면 매 주기 같은 알림이 간다.
 */
@Component
public class UnshippedReminderRule implements PipelineRule {

    private final OrderRepositoryPort orders;
    private final GetShipmentUseCase shipments;
    private final OrderEventNotifierPort notifier;
    private final PipelineMarkerPort markers;
    private final PipelineProperties properties;

    public UnshippedReminderRule(
            OrderRepositoryPort orders,
            GetShipmentUseCase shipments,
            OrderEventNotifierPort notifier,
            PipelineMarkerPort markers,
            PipelineProperties properties) {
        this.orders = orders;
        this.shipments = shipments;
        this.notifier = notifier;
        this.markers = markers;
        this.properties = properties;
    }

    @Override
    public String name() {
        return "unshipped-reminder";
    }

    @Override
    public List<String> candidates(Instant now) {
        return orders
                .findByStatusChangedBefore(OrderStatus.FUNDS_HELD, now.minus(properties.shipmentReminderAfter()))
                .stream()
                .map(Order::getId)
                .toList();
    }

    @Override
    public boolean apply(String orderId, Instant now) {
        if (shipments.getByOrderId(orderId).isPresent()) {
            return false; // 이미 발송됨
        }
        if (!markers.markOnce(name(), orderId)) {
            return false; // 이미 독촉함
        }
        Optional<Order> order = orders.findById(orderId);
        order.ifPresent(o -> notifier.shipmentReminder(o.getSellerId(), o.getId()));
        return order.isPresent();
    }
}
