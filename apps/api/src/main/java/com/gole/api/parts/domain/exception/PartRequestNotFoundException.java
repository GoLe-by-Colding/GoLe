package com.gole.api.parts.domain.exception;

import com.gole.api.common.exception.NotFoundException;

/** 부품 요청이 없음(HTTP 404). 삭제된 요청도 여기로 온다. (wanted-parts W5) */
public class PartRequestNotFoundException extends NotFoundException {

    public PartRequestNotFoundException(String requestId) {
        super("PART_REQUEST_NOT_FOUND", "부품 요청을 찾을 수 없습니다: " + requestId);
    }
}
