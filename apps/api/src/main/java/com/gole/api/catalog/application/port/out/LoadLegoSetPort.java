package com.gole.api.catalog.application.port.out;

import com.gole.api.catalog.domain.model.LegoSet;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port: 카탈로그 세트 조회. application은 이 인터페이스에만 의존하고
 * 실제 영속성 구현(adapter/out)은 인프라 계층에서 제공한다.
 */
public interface LoadLegoSetPort {

    /**
     * 세트 번호로 단건 조회.
     */
    Optional<LegoSet> loadBySetNumber(String setNumber);

    /**
     * 이름 또는 테마에 검색어가 포함된 세트 목록 조회.
     */
    List<LegoSet> searchByNameOrTheme(String query);

    /**
     * 홈 추천 세트 목록 조회 (최대 limit 개).
     */
    List<LegoSet> loadFeatured(int limit);
}
