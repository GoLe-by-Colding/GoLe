package com.gole.api.parts.application.port.out;

import java.util.Optional;

/**
 * 카탈로그 세트 확인 outbound port. catalog 컨텍스트의 인바운드 포트로 구현한다. (W2)
 *
 * <p>있으면 표시 이름을 돌려준다 — 보유자 알림 문구가 번호만이 아니라 "에펠탑(10307)"처럼 세트 이름을 쓴다.
 */
public interface PartsCatalogPort {

    /** @return 세트가 있으면 표시 이름(이름이 비어 있으면 번호), 없으면 비어 있음. 조회 장애는 예외로 올린다 */
    Optional<String> setName(String setNumber);
}
