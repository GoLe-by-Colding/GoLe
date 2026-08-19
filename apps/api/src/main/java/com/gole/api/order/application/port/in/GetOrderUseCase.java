package com.gole.api.order.application.port.in;

import com.gole.api.order.domain.model.Order;
import java.util.List;

public interface GetOrderUseCase {

    Order getById(String orderId);

    List<Order> getByBuyerId(String buyerId);

    /** 판매자 발송 관리용 판매 내역. */
    List<Order> getBySellerId(String sellerId);
}
