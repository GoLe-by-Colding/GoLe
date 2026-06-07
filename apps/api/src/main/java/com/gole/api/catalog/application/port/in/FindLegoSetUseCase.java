package com.gole.api.catalog.application.port.in;

import com.gole.api.catalog.domain.model.LegoSet;

/**
 * Inbound port: 세트 번호로 카탈로그 세트 조회 (요구사항 4.2, 4.5).
 */
public interface FindLegoSetUseCase {

    LegoSet findBySetNumber(String setNumber);
}
