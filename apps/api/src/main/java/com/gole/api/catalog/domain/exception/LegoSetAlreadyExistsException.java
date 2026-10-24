package com.gole.api.catalog.domain.exception;

import com.gole.api.common.exception.ConflictException;

/** 신규 등록이 기존 기준 정보를 덮어쓰지 않도록 충돌로 알린다. */
public class LegoSetAlreadyExistsException extends ConflictException {

    public LegoSetAlreadyExistsException(String setNumber) {
        super("LEGO_SET_ALREADY_EXISTS", "이미 등록된 세트 번호입니다. 기존 세트의 수정 기능을 이용해 주세요: " + setNumber);
    }
}
