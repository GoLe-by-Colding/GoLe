package com.gole.api.catalog.application.port.in;

import com.gole.api.catalog.domain.model.LegoSet;
import java.util.List;

/**
 * Inbound port: 홈 추천 세트 목록 조회.
 */
public interface ListFeaturedLegoSetsUseCase {

    List<LegoSet> findFeatured();
}
