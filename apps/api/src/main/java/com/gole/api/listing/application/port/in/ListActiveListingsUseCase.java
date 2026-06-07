package com.gole.api.listing.application.port.in;

import com.gole.api.listing.domain.model.Listing;
import java.util.List;

/**
 * Inbound port: 활성 리스팅 목록(탐색 기본). 본격 검색/필터는 Search 컨텍스트(요구사항 14).
 */
public interface ListActiveListingsUseCase {

    List<Listing> listActive();
}
