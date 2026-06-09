package com.gole.api.pricing.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.catalog.application.port.in.FindLegoSetUseCase;
import com.gole.api.catalog.domain.model.LegoSet;
import com.gole.api.catalog.domain.model.RetirementStatus;
import com.gole.api.pricing.application.port.in.GetTrendingSetsUseCase.TrendingSet;
import com.gole.api.pricing.application.port.out.PriceTransactionRepositoryPort;
import com.gole.api.pricing.application.port.out.PriceTransactionRepositoryPort.TradeAggregate;
import com.gole.api.pricing.application.port.out.TrendingCachePort;
import com.gole.api.pricing.domain.model.PriceTransaction;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 가짜 포트로 트렌딩 유스케이스를 검증한다(집계/캐시/카탈로그 보강). (백로그 13.4)
 */
class TrendingServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void getTrending_aggregatesAndEnrichesWithCatalogName() {
        FakeRepo repo = new FakeRepo(List.of(
                new TradeAggregate("10307", 3, 250_000),
                new TradeAggregate("99999", 1, 10_000)));
        FakeCache cache = new FakeCache();
        FindLegoSetUseCase catalog = setNumber -> {
            if ("10307".equals(setNumber)) {
                return new LegoSet("10307", "에펠탑", "Icons", 10001, 2023,
                        RetirementStatus.ACTIVE, "http://img/10307.png");
            }
            throw new RuntimeException("not found");
        };
        TrendingService service = new TrendingService(repo, cache, catalog, CLOCK);

        List<TrendingSet> result = service.getTrending(8);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).setNumber()).isEqualTo("10307");
        assertThat(result.get(0).name()).isEqualTo("에펠탑");
        assertThat(result.get(0).tradeCount()).isEqualTo(3);
        // 카탈로그에 없는 세트는 setNumber로 우아하게 대체
        assertThat(result.get(1).name()).isEqualTo("99999");
        assertThat(result.get(1).imageUrl()).isNull();
        // 결과가 캐시에 저장됨
        assertThat(cache.stored).containsKey(8);
    }

    @Test
    void getTrending_returnsCachedValue_withoutHittingRepository() {
        FakeRepo repo = new FakeRepo(List.of()); // 비어 있음 → 캐시 히트면 사용 안 됨
        FakeCache cache = new FakeCache();
        List<TrendingSet> cached = List.of(new TrendingSet("42100", "리브헬", null, 9, 500_000));
        cache.stored.put(8, cached);
        TrendingService service = new TrendingService(repo, cache, sn -> {
            throw new RuntimeException("should not be called");
        }, CLOCK);

        List<TrendingSet> result = service.getTrending(8);

        assertThat(result).isEqualTo(cached);
        assertThat(repo.called).isFalse();
    }

    @Test
    void getTrending_clampsLimitToAtLeastOne() {
        FakeRepo repo = new FakeRepo(List.of());
        TrendingService service = new TrendingService(repo, new FakeCache(), sn -> {
            throw new RuntimeException();
        }, CLOCK);

        service.getTrending(0);

        assertThat(repo.lastLimit).isEqualTo(1);
    }

    private static final class FakeRepo implements PriceTransactionRepositoryPort {
        private final List<TradeAggregate> aggregates;
        private boolean called = false;
        private int lastLimit = -1;

        FakeRepo(List<TradeAggregate> aggregates) {
            this.aggregates = aggregates;
        }

        @Override
        public PriceTransaction save(PriceTransaction transaction) {
            return transaction;
        }

        @Override
        public List<PriceTransaction> findInRangeAscending(String setNumber, Instant from, Instant to) {
            return new ArrayList<>();
        }

        @Override
        public List<TradeAggregate> findTopTradedSets(int limit, Instant since) {
            called = true;
            lastLimit = limit;
            return aggregates;
        }
    }

    private static final class FakeCache implements TrendingCachePort {
        private final Map<Integer, List<TrendingSet>> stored = new HashMap<>();

        @Override
        public Optional<List<TrendingSet>> get(int limit) {
            return Optional.ofNullable(stored.get(limit));
        }

        @Override
        public void put(int limit, List<TrendingSet> trending, Duration ttl) {
            stored.put(limit, trending);
        }
    }
}
