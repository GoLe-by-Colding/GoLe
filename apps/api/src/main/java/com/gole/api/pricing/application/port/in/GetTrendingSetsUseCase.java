package com.gole.api.pricing.application.port.in;

import java.util.List;

/**
 * Inbound port: 체결 거래량 기준 인기(트렌딩) 세트 조회. (백로그 13.4 — 인기 세트 랭킹)
 *
 * <p>집계는 비싸므로 결과는 Redis에 짧은 TTL로 캐시한다(서비스 책임).
 */
public interface GetTrendingSetsUseCase {

    List<TrendingSet> getTrending(int limit);

    /**
     * @param setNumber    카탈로그 세트 번호
     * @param name         세트명(카탈로그 미존재 시 setNumber로 대체)
     * @param imageUrl     세트 이미지(nullable)
     * @param tradeCount   체결 건수
     * @param averagePrice 평균 체결가(KRW)
     */
    record TrendingSet(
            String setNumber, String name, String imageUrl, long tradeCount, long averagePrice) {}
}
