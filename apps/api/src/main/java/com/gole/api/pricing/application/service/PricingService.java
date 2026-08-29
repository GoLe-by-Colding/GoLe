package com.gole.api.pricing.application.service;

import com.gole.api.pricing.application.port.in.GetPriceInsightsUseCase;
import com.gole.api.pricing.application.port.in.RecordExecutedPriceUseCase;
import com.gole.api.pricing.application.port.out.PriceTransactionRepositoryPort;
import com.gole.api.pricing.domain.model.ConditionGroup;
import com.gole.api.pricing.domain.model.MarketDataState;
import com.gole.api.pricing.domain.model.PriceSnapshot;
import com.gole.api.pricing.domain.model.PriceStatistics;
import com.gole.api.pricing.domain.model.PriceTransaction;
import com.gole.api.pricing.domain.model.PriceTransactionSource;
import com.gole.api.pricing.domain.model.PriceValuation;
import com.gole.api.pricing.domain.model.SetCondition;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 시세 유스케이스 구현. inbound port를 구현하고 outbound port에만 의존한다.
 * 횡단 로깅은 UseCaseLoggingAspect가 AOP로 처리.
 */
@Service
public class PricingService implements RecordExecutedPriceUseCase, GetPriceInsightsUseCase {

    private final PriceTransactionRepositoryPort repository;
    private final MarketEvidencePolicy evidencePolicy;

    public PricingService(PriceTransactionRepositoryPort repository, MarketEvidencePolicy evidencePolicy) {
        this.repository = repository;
        this.evidencePolicy = evidencePolicy;
    }

    @Override
    public void record(RecordExecutedPriceCommand command) {
        repository.save(new PriceTransaction(
                command.setNumber(),
                command.price(),
                command.quantity(),
                command.executedAt(),
                SetCondition.fromKey(command.condition()),
                PriceTransactionSource.fromKey(command.source()),
                command.sourceReference()));
    }

    @Override
    public PriceSnapshot getSnapshot(String setNumber) {
        List<PriceTransaction> allIncluded = included(repository.findInRangeAscending(setNumber, null, null));
        // 공개 헤드라인은 차트·밸류에이션과 같은 미개봉 체결 모집단을 사용한다.
        // 모든 등급을 합치면 중고 체결만 3건인 세트가 ESTABLISHED가 되면서 정작
        // 미개봉 차트와 밸류에이션은 비는 모순이 생긴다.
        List<PriceTransaction> ascending = byCondition(allIncluded, SetCondition.NEW_SEALED);
        int sampleCount = ascending.size();
        MarketDataState state = MarketDataState.fromSampleCount(sampleCount, MIN_REAL_SAMPLES);
        List<PriceTransaction> recentFirst = new ArrayList<>(ascending);
        Collections.reverse(recentFirst);
        PriceStatistics statistics =
                state == MarketDataState.ESTABLISHED ? PriceStatistics.from(setNumber, recentFirst) : null;
        PriceValuation valuation = state == MarketDataState.ESTABLISHED
                ? valuationFromIncluded(setNumber, allIncluded).orElse(null)
                : null;
        // provenance는 헤드라인뿐 아니라 같은 응답의 상태별 밸류에이션이 사용할 수 있는
        // 모든 등급 증빙을 포함한다. 미개봉은 LIVE인데 중고 등급은 데모인 경우를 FIRST_PARTY로
        // 잘못 표시하지 않는다.
        Set<PriceTransactionSource> sources =
                allIncluded.stream().map(PriceTransaction::source).collect(Collectors.toUnmodifiableSet());
        boolean demo = sources.contains(PriceTransactionSource.DEMO_SEED)
                || sources.contains(PriceTransactionSource.PLATFORM_TEST);
        return new PriceSnapshot(
                setNumber,
                state,
                MIN_REAL_SAMPLES,
                sampleCount,
                List.copyOf(recentFirst),
                statistics,
                valuation,
                sources,
                demo);
    }

    @Override
    public Optional<PriceStatistics> getStatistics(String setNumber, Instant from, Instant to) {
        List<PriceTransaction> ascending = included(repository.findInRangeAscending(setNumber, from, to));
        if (ascending.size() < MIN_REAL_SAMPLES) {
            return Optional.empty(); // 요구사항 9.5: no-data
        }
        List<PriceTransaction> recentFirst = new ArrayList<>(ascending);
        Collections.reverse(recentFirst);
        return Optional.of(PriceStatistics.from(setNumber, recentFirst));
    }

    @Override
    public List<PriceTransaction> getChart(String setNumber, Instant from, Instant to) {
        return included(repository.findInRangeAscending(setNumber, from, to));
    }

    @Override
    public List<PriceTransaction> getChart(String setNumber, SetCondition condition) {
        return included(repository.findByConditionAscending(setNumber, condition));
    }

    @Override
    public List<PriceTransaction> getHistory(String setNumber) {
        List<PriceTransaction> recentFirst =
                new ArrayList<>(included(repository.findInRangeAscending(setNumber, null, null)));
        Collections.reverse(recentFirst);
        return recentFirst;
    }

    /**
     * 상태별 밸류에이션. 근거가 강한 순서로 3단계를 밟는다.
     *
     * <pre>
     *   1) 등급 실측  — 해당 등급 체결 ≥ 3건이면 그 중앙값        (basis=GRADE)
     *   2) 그룹 실측  — 같은 ConditionGroup 체결 ≥ 3건이면 환산   (basis=GROUP)
     *   3) 감가 모델  — 미개봉 시세 × 등급 계수                    (basis=MODEL)
     * </pre>
     *
     * <p>2단계가 있는 이유 — 등급을 5개로 늘리면 등급당 표본이 흩어져 1단계를 통과하지
     * 못하고 곧장 모델로 떨어진다. 그러면 등급을 늘린 만큼 오히려 시세가 부정확해진다.
     * 그룹 단위로 한 번 받쳐 주면 해상도는 올리면서 초기 표본 부족은 흡수한다.
     */
    @Override
    public Optional<PriceValuation> getValuation(String setNumber) {
        return valuationFromIncluded(setNumber, included(repository.findInRangeAscending(setNumber, null, null)));
    }

    /**
     * 하나의 원자적 증빙 목록에서 헤드라인과 모든 상태별 밸류에이션을 함께 계산한다.
     *
     * <p>스냅샷 생성 중 등급마다 저장소를 다시 읽으면 요청 도중 새 체결이 들어올 때 표본 수,
     * 통계, 밸류에이션의 기준 시점이 달라질 수 있다. 공개 응답은 최초 한 번 읽은 목록만
     * 사용해 내부 일관성을 보장한다.
     */
    private Optional<PriceValuation> valuationFromIncluded(String setNumber, List<PriceTransaction> allIncluded) {
        List<PriceTransaction> sealed = byCondition(allIncluded, SetCondition.NEW_SEALED);
        if (sealed.size() < MIN_REAL_SAMPLES) {
            return Optional.empty();
        }
        long marketPrice = sealed.get(sealed.size() - 1).price();

        List<PriceValuation.ConditionValuation> conditions = new ArrayList<>();
        for (SetCondition condition : SetCondition.values()) {
            List<PriceTransaction> series = byCondition(allIncluded, condition);
            if (series.size() >= MIN_REAL_SAMPLES) {
                conditions.add(PriceValuation.grade(condition, marketPrice, medianPrice(series), series.size()));
                continue;
            }

            ConditionGroup group = condition.group();
            if (group.members().size() > 1) {
                List<PriceTransaction> pooled = allIncluded.stream()
                        .filter(transaction -> group.members().contains(transaction.condition()))
                        .toList();
                if (pooled.size() >= MIN_REAL_SAMPLES) {
                    conditions.add(PriceValuation.group(
                            condition, marketPrice, medianPrice(pooled), medianFactor(pooled), pooled.size()));
                    continue;
                }
            }

            conditions.add(PriceValuation.model(condition, marketPrice));
        }
        return Optional.of(new PriceValuation(setNumber, marketPrice, List.copyOf(conditions)));
    }

    /** 해당 단위(등급 또는 그룹)의 실데이터를 신뢰하기 위한 최소 표본 수. */
    private static final int MIN_REAL_SAMPLES = 3;

    private List<PriceTransaction> included(List<PriceTransaction> transactions) {
        return transactions.stream().filter(evidencePolicy::includes).toList();
    }

    private static List<PriceTransaction> byCondition(List<PriceTransaction> transactions, SetCondition condition) {
        return transactions.stream()
                .filter(transaction -> transaction.condition() == condition)
                .toList();
    }

    private static long medianPrice(List<PriceTransaction> ascending) {
        long[] prices =
                ascending.stream().mapToLong(PriceTransaction::price).sorted().toArray();
        int n = prices.length;
        return n % 2 == 1 ? prices[n / 2] : Math.round((prices[n / 2 - 1] + prices[n / 2]) / 2.0);
    }

    /**
     * 그룹 표본의 대표 감가 계수 — 표본이 속한 등급 계수들의 중앙값.
     *
     * <p>{@link #medianPrice}와 <b>같은 표본에서 같은 방식(중앙값)으로</b> 뽑는 것이 핵심이다.
     * 앵커 가격이 표본 가중인데 기준 계수만 등급 평균이면 둘의 기준점이 어긋난다. 표본이 한
     * 등급으로만 차 있으면 그 등급 계수가 그대로 나오고, 반반이면 두 계수의 중간이 나온다.
     *
     * @return 표본이 비었으면 0 — 호출측({@code PriceValuation.group})이 감가 모델로 물러난다
     */
    private static double medianFactor(List<PriceTransaction> pooled) {
        double[] factors = pooled.stream()
                .mapToDouble(t -> t.condition().factor())
                .sorted()
                .toArray();
        int n = factors.length;
        if (n == 0) {
            return 0;
        }
        return n % 2 == 1 ? factors[n / 2] : (factors[n / 2 - 1] + factors[n / 2]) / 2.0;
    }
}
