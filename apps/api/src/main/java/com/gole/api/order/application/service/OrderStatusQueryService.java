package com.gole.api.order.application.service;

import com.gole.api.order.application.port.in.ListOrdersByStatusUseCase;
import com.gole.api.order.application.port.out.OrderRepositoryPort;
import com.gole.api.order.domain.model.Order;
import com.gole.api.order.domain.model.OrderStatus;
import java.util.List;
import org.springframework.stereotype.Service;

/** 상태별 주문 조회. */
@Service
public class OrderStatusQueryService implements ListOrdersByStatusUseCase {

    private final OrderRepositoryPort orders;

    public OrderStatusQueryService(OrderRepositoryPort orders) {
        this.orders = orders;
    }

    @Override
    public List<Order> listByStatus(OrderStatus status) {
        return orders.findByStatus(status);
    }
}
