package com.gole.api.order.application.port.in;

import com.gole.api.order.domain.model.Order;
import com.gole.api.order.domain.model.OrderStatus;
import java.util.List;

/** Inbound port: 상태별 주문 목록(운영 예외 큐 등 운영 화면용). */
public interface ListOrdersByStatusUseCase {

    List<Order> listByStatus(OrderStatus status);
}
