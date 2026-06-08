package com.gole.api.catalog.application.port.in;

import com.gole.api.catalog.domain.model.LegoSet;
import java.util.List;

/**
 * Inbound port: 전체 카탈로그 세트 목록 조회(관리자). (요구사항 4)
 */
public interface ListLegoSetsUseCase {

    List<LegoSet> all();
}
