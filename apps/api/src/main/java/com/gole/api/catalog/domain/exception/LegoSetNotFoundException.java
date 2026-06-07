package com.gole.api.catalog.domain.exception;

import com.gole.api.common.exception.NotFoundException;

/**
 * 요구사항 4.5: 존재하지 않는 세트 번호 조회 시 not-found.
 */
public class LegoSetNotFoundException extends NotFoundException {

    public LegoSetNotFoundException(String setNumber) {
        super("LEGO_SET_NOT_FOUND", "LEGO set not found: " + setNumber);
    }
}
