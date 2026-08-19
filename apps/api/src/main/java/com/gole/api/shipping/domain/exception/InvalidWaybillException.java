package com.gole.api.shipping.domain.exception;

import com.gole.api.common.exception.BadRequestException;

/** 송장번호 형식 위반. (R1.3) */
public class InvalidWaybillException extends BadRequestException {

    public InvalidWaybillException(String message) {
        super("INVALID_WAYBILL", message);
    }
}
