package com.gole.api.catalog.application.port.out;

import com.gole.api.catalog.domain.model.LegoSet;
import java.util.List;

/**
 * Outbound port: 카탈로그 세트 쓰기/전체 조회(관리자용). 읽기 전용 {@link LoadLegoSetPort}와 분리한다.
 */
public interface CatalogAdminPort {

    /** 세트를 저장(신규/갱신)한다. featured 는 홈 추천 노출 여부. */
    LegoSet save(LegoSet set, boolean featured);

    /** 전체 세트 목록(관리자 대시보드용). */
    List<LegoSet> findAll();
}
