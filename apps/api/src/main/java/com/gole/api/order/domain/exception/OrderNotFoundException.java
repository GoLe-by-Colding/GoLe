package com.gole.api.order.domain.exception;

import com.gole.api.common.exception.NotFoundException;

public class OrderNotFoundException extends NotFoundException {

    public OrderNotFoundException(String orderId) {
        super("ORDER_NOT_FOUND", "주문을 찾을 수 없습니다");
    }
}
