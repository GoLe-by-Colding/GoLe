package com.gole.api.catalog.application.port.in;

import com.gole.api.catalog.domain.model.RetirementStatus;

/**
 * Inbound port: 카탈로그 세트 수정/추천 토글(관리자). (admin-console 요구사항 7.3, 7.4)
 *
 * <p>등록({@link CreateLegoSetUseCase})과 분리한 이유는 "존재하는 세트만 갱신"이라는 사전조건이
 * 다르기 때문이다. 없는 세트를 수정하면 404가 된다.
 */
public interface UpdateLegoSetUseCase {

    void update(UpdateLegoSetCommand command);

    /** 홈 추천 노출 플래그만 갱신한다. */
    void setFeatured(String setNumber, boolean featured);

    record UpdateLegoSetCommand(
            String setNumber,
            String name,
            String theme,
            int pieceCount,
            int releaseYear,
            RetirementStatus retirementStatus,
            String imageUrl,
            boolean featured) {}
}
