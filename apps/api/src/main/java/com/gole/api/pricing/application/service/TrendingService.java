package com.gole.api.pricing.application.service;

import com.gole.api.catalog.application.port.in.FindLegoSetUseCase;
import com.gole.api.catalog.domain.model.LegoSet;
import com.gole.api.pricing.application.port.in.GetTrendingSetsUseCase;
import com.gole.api.pricing.application.port.out.PriceTransactionRepositoryPort;
import com.gole.api.pricing.application.port.out.PriceTransactionRepositoryPort.TradeAggregate;
import com.gole.api.pricing.application.port.out.TrendingCachePort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 인기(트렌딩) 세트 유스케이스. 최근 N일 체결 건수 집계를 Redis에 짧은 TTL로 캐시(cache-aside)하고,
 * 카탈로그 인바운드 포트로 세트명/이미지를 보강한다. 윈도우 내 데이터가 없으면 전체 기간으로 폴백한다.
 * (백로그 13.4)
 *
 * <p>컨텍스트 간 연동 규칙(NFR-3): 다른 컨텍스트(catalog)의 인바운드 포트에만 의존한다.
 */
@Service
public class TrendingService implements GetTrendingSetsUseCase {

    private static final int MAX_LIMIT = 50;
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final Duration TRENDING_WINDOW = Duration.ofDays(30);

    private final PriceTransactionRepositoryPort repository;
    private final TrendingCachePort cache;
    private final FindLegoSetUseCase findLegoSet;
    private final Clock clock;
    private final MarketEvidencePolicy evidencePolicy;

    public TrendingService(
            PriceTransactionRepositoryPort repository,
            TrendingCachePort cache,
            FindLegoSetUseCase findLegoSet,
            Clock clock,
            MarketEvidencePolicy evidencePolicy) {
        this.repository = repository;
        this.cache = cache;
        this.findLegoSet = findLegoSet;
        this.clock = clock;
        this.evidencePolicy = evidencePolicy;
    }

    @Override
    public List<TrendingSet> getTrending(int limit) {
        int effectiveLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);

        Optional<List<TrendingSet>> cached = cache.get(effectiveLimit);
        if (cached.isPresent()) {
            return cached.get();
        }

        // 최근 30일 우선, 데이터가 없으면 전체 기간으로 폴백(희소 데이터에서도 표시 유지).
        Instant since = Instant.now(clock).minus(TRENDING_WINDOW);
        List<TradeAggregate> aggregates =
                repository.findTopTradedSets(effectiveLimit, since, evidencePolicy.includedSources());
        if (aggregates.isEmpty()) {
            aggregates = repository.findTopTradedSets(effectiveLimit, null, evidencePolicy.includedSources());
        }

        List<TrendingSet> trending = aggregates.stream().map(this::enrich).toList();

        cache.put(effectiveLimit, trending, CACHE_TTL);
        return trending;
    }

    /** 카탈로그에서 세트명/이미지를 보강한다. 미존재/오류 시 setNumber로 우아하게 대체한다. */
    private TrendingSet enrich(TradeAggregate aggregate) {
        String name = aggregate.setNumber();
        String imageUrl = null;
        try {
            LegoSet set = findLegoSet.findBySetNumber(aggregate.setNumber());
            name = set.getName();
            imageUrl = set.getImageUrl();
        } catch (RuntimeException ignored) {
            // 카탈로그에 없거나 조회 실패해도 랭킹 자체는 제공한다.
        }
        return new TrendingSet(aggregate.setNumber(), name, imageUrl, aggregate.tradeCount(), aggregate.averagePrice());
    }
}
