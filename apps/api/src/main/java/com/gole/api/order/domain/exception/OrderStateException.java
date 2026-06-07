package com.gole.api.order.domain.exception;

import com.gole.api.common.exception.DomainException;

/**
 * 허용되지 않는 주문 상태 전이.
 */
public class OrderStateException extends DomainException {

    public OrderStateException(String message) {
        super("ORDER_INVALID_STATE", message);
    }
}
