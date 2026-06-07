package com.gole.api.order.application.port.in;

import com.gole.api.order.domain.model.Order;

public interface GetOrderUseCase {

    Order getById(String orderId);
}
