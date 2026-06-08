package com.gole.api.pricing.application.port.out;

import com.gole.api.pricing.application.port.in.GetTrendingSetsUseCase.TrendingSet;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port: 인기 세트 랭킹 캐시. (백로그 13.4 — Redis 캐싱)
 *
 * <p>구현(Redis 등)은 어댑터가 담당하며, 캐시 장애가 기능을 중단시키지 않도록
 * 조회 실패는 빈 Optional로, 저장 실패는 무시(graceful degradation)해야 한다.
 */
public interface TrendingCachePort {

    Optional<List<TrendingSet>> get(int limit);

    void put(int limit, List<TrendingSet> trending, Duration ttl);
}
