package com.gole.api.shipping.domain.exception;

import com.gole.api.common.exception.ConflictException;

/** 배송 상태 전이 규칙 위반(역행·종결 후 변경 등). */
public class ShipmentStateException extends ConflictException {

    public ShipmentStateException(String message) {
        super("SHIPMENT_STATE_CONFLICT", message);
    }
}
