package com.gole.api.parts.domain.exception;

import com.gole.api.common.exception.BadRequestException;

/** 부품 요청 입력 규칙 위반(HTTP 400). 어느 칸이 틀렸는지는 메시지로만 알린다. (wanted-parts W1) */
public class InvalidPartRequestException extends BadRequestException {

    public InvalidPartRequestException(String message) {
        super("PART_REQUEST_INVALID", message);
    }
}
