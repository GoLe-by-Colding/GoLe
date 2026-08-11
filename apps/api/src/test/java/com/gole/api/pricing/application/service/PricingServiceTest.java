package com.gole.api.pricing.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.pricing.application.port.in.RecordExecutedPriceUseCase.RecordExecutedPriceCommand;
import com.gole.api.pricing.application.port.out.PriceTransactionRepositoryPort;
import com.gole.api.pricing.application.port.out.PriceTransactionRepositoryPort.TradeAggregate;
import com.gole.api.pricing.domain.model.PriceStatistics;
import com.gole.api.pricing.domain.model.PriceTransaction;
import com.gole.api.pricing.domain.model.PriceValuation;
import com.gole.api.pricing.domain.model.SetCondition;
import com.gole.api.pricing.domain.model.ValuationBasis;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PricingServiceTest {

    private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");

    private InMemoryRepo repo;
    private PricingService service;

    @BeforeEach
    void setUp() {
        repo = new InMemoryRepo();
        service = new PricingService(repo);
    }

    @Test
    void statistics_emptyWhenNoData() {
        Optional<PriceStatistics> stats = service.getStatistics("99999", null, null);
        assertThat(stats).isEmpty();
    }

    @Test
    void statistics_computesLatestHighestLowest() {
        service.record(new RecordExecutedPriceCommand("10307", 100, 1, BASE));
        service.record(new RecordExecutedPriceCommand("10307", 300, 1, BASE.plus(1, ChronoUnit.DAYS)));
        service.record(new RecordExecutedPriceCommand("10307", 200, 1, BASE.plus(2, ChronoUnit.DAYS)));

        PriceStatistics stats = service.getStatistics("10307", null, null).orElseThrow();
        assertThat(stats.latestPrice()).isEqualTo(200); // 가장 최근
        assertThat(stats.highestPrice()).isEqualTo(300);
        assertThat(stats.lowestPrice()).isEqualTo(100);
        assertThat(stats.transactionCount()).isEqualTo(3);
    }

    @Test
    void chart_isAscending_history_isDescending() {
        service.record(new RecordExecutedPriceCommand("10307", 100, 1, BASE));
        service.record(new RecordExecutedPriceCommand("10307", 200, 1, BASE.plus(1, ChronoUnit.DAYS)));

        assertThat(service.getChart("10307", null, null))
                .extracting(PriceTransaction::price)
                .containsExactly(100L, 200L);
        assertThat(service.getHistory("10307"))
                .extracting(PriceTransaction::price)
                .containsExactly(200L, 100L);
    }

    // --- 밸류에이션 3단계 폴백 (등급 실측 → 그룹 실측 → 감가 모델) ---

    @Test
    void valuation_usesGradeMedian_whenGradeHasEnoughSamples() {
        record(SetCondition.NEW_SEALED, 1_000_000, 1_000_000, 1_000_000);
        record(SetCondition.USED_GOOD, 700_000, 720_000, 740_000);

        PriceValuation.ConditionValuation good = conditionOf(valuation(), SetCondition.USED_GOOD);
        assertThat(good.basis()).isEqualTo(ValuationBasis.GRADE);
        assertThat(good.sampleCount()).isEqualTo(3);
        assertThat(good.fairPrice()).isEqualTo(720_000); // 중앙값
    }

    @Test
    void valuation_fallsBackToGroup_whenGradeIsThinButGroupIsNot() {
        record(SetCondition.NEW_SEALED, 1_000_000, 1_000_000, 1_000_000);
        // LIKE_NEW는 1건뿐이지만 같은 COMPLETE 그룹의 USED_GOOD이 3건 → 그룹으로 받친다.
        record(SetCondition.LIKE_NEW, 900_000);
        record(SetCondition.USED_GOOD, 700_000, 720_000, 740_000);

        PriceValuation.ConditionValuation likeNew = conditionOf(valuation(), SetCondition.LIKE_NEW);
        assertThat(likeNew.basis()).isEqualTo(ValuationBasis.GROUP);
        assertThat(likeNew.sampleCount()).isEqualTo(4); // LIKE_NEW 1건 + USED_GOOD 3건
        // 그룹 중앙값 730,000(700·720·740·900의 중앙)을 앵커로 LIKE_NEW 계수로 환산한다.
        // 미개봉에서 통째로 외삽하는 모델값(880,000)과는 달라야 한다.
        assertThat(likeNew.fairPrice())
                .isNotEqualTo(
                        PriceValuation.model(SetCondition.LIKE_NEW, 1_000_000).fairPrice());
    }

    @Test
    void valuation_groupAnchorTracksTheDominantGrade_notTheGroupAverage() {
        // 회귀 방지. DAMAGED 표본 0건 + USED_FAIR 5건이 계수(0.62)대로 체결된 상황.
        // 그룹 폴백은 "대상 등급 표본이 얇을 때"만 타므로 풀이 한 등급에 쏠리는 게 기본이다.
        // 기준계수를 등급 계수 평균(0.535)으로 잡으면 하자 등급이 16% 비싸게 나온다.
        record(SetCondition.NEW_SEALED, 1_000_000, 1_000_000, 1_000_000);
        record(SetCondition.USED_FAIR, 620_000, 620_000, 620_000, 620_000, 620_000);

        PriceValuation.ConditionValuation damaged = conditionOf(valuation(), SetCondition.DAMAGED);
        assertThat(damaged.basis()).isEqualTo(ValuationBasis.GROUP);
        assertThat(damaged.sampleCount()).isEqualTo(5);
        assertThat(damaged.fairPrice()).isEqualTo(450_000); // 620,000 * 0.45/0.62
    }

    @Test
    void valuation_groupAnchorDoesNotUndervalueTheBetterGradeInADominatedPool() {
        // 반대 방향. LIKE_NEW 표본 0건 + USED_GOOD 5건 → 기준계수는 0.78이어야 한다.
        record(SetCondition.NEW_SEALED, 1_000_000, 1_000_000, 1_000_000);
        record(SetCondition.USED_GOOD, 780_000, 780_000, 780_000, 780_000, 780_000);

        PriceValuation.ConditionValuation likeNew = conditionOf(valuation(), SetCondition.LIKE_NEW);
        assertThat(likeNew.basis()).isEqualTo(ValuationBasis.GROUP);
        assertThat(likeNew.fairPrice()).isEqualTo(880_000); // 780,000 * 0.88/0.78
    }

    @Test
    void valuation_fallsBackToModel_whenGroupIsAlsoThin() {
        record(SetCondition.NEW_SEALED, 1_000_000, 1_000_000, 1_000_000);
        record(SetCondition.DAMAGED, 400_000); // INCOMPLETE 그룹 전체가 1건

        PriceValuation.ConditionValuation damaged = conditionOf(valuation(), SetCondition.DAMAGED);
        assertThat(damaged.basis()).isEqualTo(ValuationBasis.MODEL);
        assertThat(damaged.sampleCount()).isZero();
        assertThat(damaged.fairPrice())
                .isEqualTo(PriceValuation.model(SetCondition.DAMAGED, 1_000_000).fairPrice());
    }

    @Test
    void valuation_sealedNeverUsesGroupBasis() {
        // SEALED 그룹은 NEW_SEALED 단독이라 그룹 표본이 등급 표본과 같다. 굳이 GROUP을 거칠 이유가 없다.
        record(SetCondition.NEW_SEALED, 1_000_000, 1_100_000); // 2건 → 등급 미달

        PriceValuation.ConditionValuation sealed = conditionOf(valuation(), SetCondition.NEW_SEALED);
        assertThat(sealed.basis()).isEqualTo(ValuationBasis.MODEL);
    }

    @Test
    void valuation_coversEveryGrade() {
        record(SetCondition.NEW_SEALED, 1_000_000, 1_000_000, 1_000_000);

        assertThat(valuation().conditions())
                .extracting(PriceValuation.ConditionValuation::condition)
                .containsExactly(SetCondition.values());
    }

    @Test
    void valuation_readsLegacyKeysAsMigratedGrades() {
        // 3단계 시절 저장된 체결가도 새 등급 표본으로 잡혀야 한다. 안 그러면 확장 순간 시세가 후퇴한다.
        service.record(new RecordExecutedPriceCommand("10307", 1_000_000, 1, BASE, "new_sealed"));
        service.record(new RecordExecutedPriceCommand("10307", 700_000, 1, BASE, "used_complete"));
        service.record(new RecordExecutedPriceCommand("10307", 720_000, 1, BASE, "used_complete"));
        service.record(new RecordExecutedPriceCommand("10307", 740_000, 1, BASE, "used_complete"));

        PriceValuation.ConditionValuation good = conditionOf(valuation(), SetCondition.USED_GOOD);
        assertThat(good.basis()).isEqualTo(ValuationBasis.GRADE);
        assertThat(good.sampleCount()).isEqualTo(3);
        assertThat(good.fairPrice()).isEqualTo(720_000);
    }

    @Test
    void valuation_emptyWhenNoTrades() {
        assertThat(service.getValuation("99999")).isEmpty();
    }

    private PriceValuation valuation() {
        return service.getValuation("10307").orElseThrow();
    }

    private void record(SetCondition condition, long... prices) {
        for (int i = 0; i < prices.length; i++) {
            service.record(new RecordExecutedPriceCommand(
                    "10307", prices[i], 1, BASE.plus(i, ChronoUnit.DAYS), condition.key()));
        }
    }

    private static PriceValuation.ConditionValuation conditionOf(PriceValuation valuation, SetCondition condition) {
        return valuation.conditions().stream()
                .filter(c -> c.condition() == condition)
                .findFirst()
                .orElseThrow();
    }

    private static final class InMemoryRepo implements PriceTransactionRepositoryPort {
        private final List<PriceTransaction> store = new ArrayList<>();

        @Override
        public PriceTransaction save(PriceTransaction transaction) {
            store.add(transaction);
            return transaction;
        }

        @Override
        public List<PriceTransaction> findInRangeAscending(String setNumber, Instant from, Instant to) {
            return store.stream()
                    .filter(t -> t.setNumber().equals(setNumber))
                    .filter(t -> from == null || !t.executedAt().isBefore(from))
                    .filter(t -> to == null || !t.executedAt().isAfter(to))
                    .sorted(Comparator.comparing(PriceTransaction::executedAt))
                    .toList();
        }

        @Override
        public List<PriceTransaction> findByConditionAscending(String setNumber, SetCondition condition) {
            return findByConditionsAscending(setNumber, List.of(condition));
        }

        @Override
        public List<PriceTransaction> findByConditionsAscending(String setNumber, List<SetCondition> conditions) {
            return store.stream()
                    .filter(t -> t.setNumber().equals(setNumber))
                    .filter(t -> conditions.contains(t.condition()))
                    .sorted(Comparator.comparing(PriceTransaction::executedAt))
                    .toList();
        }

        @Override
        public List<TradeAggregate> findTopTradedSets(int limit, java.time.Instant since) {
            return List.of();
        }
    }
}
