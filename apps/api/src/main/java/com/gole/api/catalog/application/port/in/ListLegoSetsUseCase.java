package com.gole.api.catalog.application.port.in;

import com.gole.api.catalog.domain.model.LegoSet;
import java.util.List;

/**
 * Inbound port: 전체 카탈로그 세트 목록 조회(관리자). (요구사항 4)
 */
public interface ListLegoSetsUseCase {

    /** 관리자 화면은 도메인 정보와 홈 추천 여부를 함께 편집하므로 플래그를 잃지 않는다. */
    record LegoSetSummary(LegoSet set, boolean featured) {}

    List<LegoSetSummary> all();
}
