package com.gole.api.parts.domain.exception;

import com.gole.api.common.exception.ConflictException;

/** 이미 마감된 요청을 다시 마감하려 함(HTTP 409). (wanted-parts W6) */
public class PartRequestNotOpenException extends ConflictException {

    public PartRequestNotOpenException(String requestId) {
        super("PART_REQUEST_NOT_OPEN", "열린 부품 요청이 아닙니다: " + requestId);
    }
}
