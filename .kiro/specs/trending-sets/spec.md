# Trending Sets (Redis 캐싱 + 인기 세트 랭킹) — Spec

> lego-marketplace 백로그 §13.4 구현. 체결 거래량 기준 인기 레고 세트 랭킹을 제공하고, 집계 결과를 Redis에 짧은 TTL로 캐시한다.

## Requirements (EARS)
- T1.1 WHEN 인기 세트 목록을 요청하면, 시스템은 체결 거래(`price_transactions`) 건수 기준 상위 N개 세트를 거래건수 내림차순으로 반환해야 한다.
- T1.2 각 항목은 세트번호·세트명·이미지·체결건수·평균체결가를 포함해야 하며, 카탈로그에 세트가 없으면 세트번호로 우아하게 대체해야 한다.
- T1.3 시스템은 집계 결과를 Redis에 짧은 TTL(60초)로 캐시하고, 캐시 히트 시 재집계 없이 반환해야 한다.
- T1.4 IF Redis가 일시 장애이면, 시스템은 캐시 미스로 처리해 기능을 중단하지 않아야 한다(graceful degradation).
- T1.5 limit는 1~50으로 클램프해야 한다(기본 8).
- T1.6 WHEN 홈 화면을 열면, 프론트는 "지금 뜨는 세트" 섹션에 랭킹을 표시하고, 데이터가 없으면 빈 상태를 보여줘야 한다.

## Design
- 백엔드(pricing 컨텍스트):
  - port-in `GetTrendingSetsUseCase.getTrending(limit)` → `List<TrendingSet>`.
  - port-out 확장 `PriceTransactionRepositoryPort.findTopTradedSets(limit)` → `TradeAggregate(setNumber, tradeCount, averagePrice)` (MongoDB aggregation: group by setNumber → count, avg(price), sort desc, limit).
  - port-out `TrendingCachePort`(get/put) — Redis 어댑터(`RedisTrendingCacheAdapter`, `StringRedisTemplate`, Base64 라인 인코딩으로 Jackson 버전 비의존, 예외 흡수).
  - service `TrendingService`: cache-aside. 카탈로그 인바운드 포트(`FindLegoSetUseCase`)로 세트명/이미지 보강(컨텍스트 간 in-port 의존, NFR-3).
  - adapter-in `TrendingController` `GET /api/v1/pricing/trending?limit=8`.
- 프론트(FSD):
  - `entities/pricing`: `TrendingSet` 타입 + `fetchTrendingSets(limit)`.
  - `widgets/trending-sets`: 표현 위젯(랭킹 리스트, 빈 상태). 데이터는 view에서 로드해 props로 주입.
  - `views/home`: `loadTrending()` 후 "지금 뜨는 세트" 섹션 렌더.

## Tasks
- [x] B1 port-in GetTrendingSetsUseCase + TrendingSet
- [x] B2 port-out findTopTradedSets(+TradeAggregate), TrendingCachePort
- [x] B3 TrendingService(cache-aside + 카탈로그 보강)
- [x] B4 MongoDB aggregation in PriceTransactionPersistenceAdapter
- [x] B5 RedisTrendingCacheAdapter(Base64, 장애 흡수)
- [x] B6 TrendingController
- [x] B7 TrendingServiceTest (캐시 히트/미스, 보강 폴백, limit 클램프)
- [x] F1 entities/pricing TrendingSet + fetchTrendingSets
- [x] F2 widgets/trending-sets
- [x] F3 home view 섹션 연동
- [x] F4 typecheck/lint/fsd:lint + build 통과
- [ ] D1 커밋·PR·머지·배포·스모크

## 후속 백로그
- [x] 시간 윈도우(최근 30일) 기반 트렌딩 + 전체기간 폴백
- [ ] 세트별 통계(statistics) 응답도 Redis 캐싱
- [ ] 조회수/위시리스트 신호를 랭킹에 반영
