package com.gole.api.shipping.domain.exception;

import com.gole.api.common.exception.NotFoundException;

/** 배송 정보 없음. */
public class ShipmentNotFoundException extends NotFoundException {

    public ShipmentNotFoundException(String orderId) {
        super("SHIPMENT_NOT_FOUND", "등록된 운송장이 없습니다: " + orderId);
    }
}
