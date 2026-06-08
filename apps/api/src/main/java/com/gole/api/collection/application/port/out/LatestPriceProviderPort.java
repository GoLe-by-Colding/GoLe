package com.gole.api.collection.application.port.out;

import java.util.Optional;

/**
 * 최근 체결가 조회 outbound port (CROSS-CONTEXT: Pricing 연동). 컬렉션 가치 추정(요구사항 11.5)에서
 * 카탈로그 세트의 최근 체결가를 가져온다. 거래가 없으면 비어있음.
 */
public interface LatestPriceProviderPort {

    /** 주어진 카탈로그 세트 번호의 최근 체결가. 거래가 없으면 비어있음. */
    Optional<Long> latestPrice(String setNumber);
}
