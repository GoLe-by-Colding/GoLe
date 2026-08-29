package com.gole.api.order.application.service;

import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.order.application.port.in.PrepareShipmentRegistrationUseCase;
import com.gole.api.order.application.port.out.OrderRepositoryPort;
import com.gole.api.order.domain.exception.OrderNotFoundException;
import com.gole.api.order.domain.model.Order;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 호출자의 Mongo 트랜잭션 안에서 주문 버전으로 운송장 등록을 선점한다. */
@Service
public class OrderShipmentTransitionService implements PrepareShipmentRegistrationUseCase {

    private final OrderRepositoryPort orders;
    private final Clock clock;

    public OrderShipmentTransitionService(OrderRepositoryPort orders, Clock clock) {
        this.orders = orders;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Order prepare(String orderId, String sellerId) {
        Order order = orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        if (!order.getSellerId().equals(sellerId)) {
            throw new ForbiddenException("SHIPMENT_ACCESS_DENIED", "주문의 판매자만 운송장을 등록할 수 있습니다");
        }
        if (!order.registerShipment(Instant.now(clock))) {
            return order;
        }
        return orders.save(order);
    }
}
