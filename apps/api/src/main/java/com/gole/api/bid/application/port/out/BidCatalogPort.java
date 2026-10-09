package com.gole.api.bid.application.port.out;

import java.util.Optional;

/** Outbound port: 카탈로그 세트 확인. 알림 문구에 세트 이름을 쓴다. */
public interface BidCatalogPort {

    /** @return 세트가 있으면 표시 이름, 없으면 비어 있음. 조회 장애는 예외로 올린다 */
    Optional<String> setName(String setNumber);
}
