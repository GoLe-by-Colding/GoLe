package com.gole.api.catalog.application.port.in;

import com.gole.api.catalog.domain.model.LegoSet;
import java.util.List;

/**
 * Inbound port: 이름/테마로 카탈로그 세트 검색 (요구사항 4.3).
 */
public interface SearchLegoSetsUseCase {

    List<LegoSet> search(String query);
}
