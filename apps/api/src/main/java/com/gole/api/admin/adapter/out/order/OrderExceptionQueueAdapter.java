package com.gole.api.admin.adapter.out.order;

import com.gole.api.admin.application.port.out.ExceptionQueueOrderPort;
import com.gole.api.order.application.port.in.GetOrderPipelinePolicyUseCase;
import com.gole.api.order.application.port.in.GetOrderUseCase;
import com.gole.api.order.application.port.in.ListOrdersByStatusUseCase;
import com.gole.api.order.domain.model.Order;
import com.gole.api.order.domain.model.OrderStatus;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 주문 컨텍스트 통합 어댑터. 주문 도메인 객체를 예외 큐에 필요한 사실로 환원한다. */
@Component
public class OrderExceptionQueueAdapter implements ExceptionQueueOrderPort {

    private final ListOrdersByStatusUseCase ordersByStatus;
    private final GetOrderUseCase orders;
    private final GetOrderPipelinePolicyUseCase pipelinePolicy;

    public OrderExceptionQueueAdapter(
            ListOrdersByStatusUseCase ordersByStatus,
            GetOrderUseCase orders,
            GetOrderPipelinePolicyUseCase pipelinePolicy) {
        this.ordersByStatus = ordersByStatus;
        this.orders = orders;
        this.pipelinePolicy = pipelinePolicy;
    }

    @Override
    public List<QueueOrder> disputedOrders() {
        return ordersByStatus.listByStatus(OrderStatus.DISPUTED).stream()
                .map(OrderExceptionQueueAdapter::toQueueOrder)
                .toList();
    }

    @Override
    public Optional<QueueOrder> findOrder(String orderId) {
        try {
            return Optional.of(toQueueOrder(orders.getById(orderId)));
        } catch (RuntimeException missing) {
            return Optional.empty();
        }
    }

    @Override
    public Thresholds thresholds() {
        var policy = pipelinePolicy.policy();
        return new Thresholds(
                policy.disputeEscalationAfter(),
                policy.carrierPickupTimeout(),
                policy.transitStallAfter(),
                policy.trackerUnknownAfter());
    }

    private static QueueOrder toQueueOrder(Order order) {
        return new QueueOrder(
                order.getId(),
                order.getStatus().name().toLowerCase(Locale.ROOT),
                order.getStatus() == OrderStatus.FUNDS_HELD || order.getStatus() == OrderStatus.DISPUTED,
                order.getBuyerId(),
                order.getSellerId(),
                order.getAmount(),
                order.getStatusChangedAt(),
                order.getDisputeOpenedAt(),
                order.getDisputeReason() == null
                        ? null
                        : order.getDisputeReason().label(),
                order.getDisputeDetail());
    }
}
