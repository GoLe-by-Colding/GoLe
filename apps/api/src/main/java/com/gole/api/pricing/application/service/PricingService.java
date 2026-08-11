package com.gole.api.pricing.application.service;

import com.gole.api.pricing.application.port.in.GetPriceInsightsUseCase;
import com.gole.api.pricing.application.port.in.RecordExecutedPriceUseCase;
import com.gole.api.pricing.application.port.out.PriceTransactionRepositoryPort;
import com.gole.api.pricing.domain.model.ConditionGroup;
import com.gole.api.pricing.domain.model.PriceStatistics;
import com.gole.api.pricing.domain.model.PriceTransaction;
import com.gole.api.pricing.domain.model.PriceValuation;
import com.gole.api.pricing.domain.model.SetCondition;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 시세 유스케이스 구현. inbound port를 구현하고 outbound port에만 의존한다.
 * 횡단 로깅은 UseCaseLoggingAspect가 AOP로 처리.
 */
@Service
public class PricingService implements RecordExecutedPriceUseCase, GetPriceInsightsUseCase {

    private final PriceTransactionRepositoryPort repository;

    public PricingService(PriceTransactionRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public void record(RecordExecutedPriceCommand command) {
        repository.save(new PriceTransaction(
                command.setNumber(),
                command.price(),
                command.quantity(),
                command.executedAt(),
                SetCondition.fromKey(command.condition())));
    }

    @Override
    public Optional<PriceStatistics> getStatistics(String setNumber, Instant from, Instant to) {
        List<PriceTransaction> ascending = repository.findInRangeAscending(setNumber, from, to);
        if (ascending.isEmpty()) {
            return Optional.empty(); // 요구사항 9.5: no-data
        }
        List<PriceTransaction> recentFirst = new ArrayList<>(ascending);
        Collections.reverse(recentFirst);
        return Optional.of(PriceStatistics.from(setNumber, recentFirst));
    }

    @Override
    public List<PriceTransaction> getChart(String setNumber, Instant from, Instant to) {
        return repository.findInRangeAscending(setNumber, from, to);
    }

    @Override
    public List<PriceTransaction> getChart(String setNumber, SetCondition condition) {
        return repository.findByConditionAscending(setNumber, condition);
    }

    @Override
    public List<PriceTransaction> getHistory(String setNumber) {
        List<PriceTransaction> recentFirst = new ArrayList<>(repository.findInRangeAscending(setNumber, null, null));
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
        Optional<PriceStatistics> stats = getStatistics(setNumber, null, null);
        if (stats.isEmpty()) {
            return Optional.empty();
        }
        // 시장 기준가: 미개봉 최근 체결가가 있으면 그 값, 없으면 전체 최근 체결가.
        List<PriceTransaction> sealed = repository.findByConditionAscending(setNumber, SetCondition.NEW_SEALED);
        long marketPrice = sealed.isEmpty()
                ? stats.get().latestPrice()
                : sealed.get(sealed.size() - 1).price();

        // 그룹 표본은 소속 등급마다 다시 읽지 않고 그룹당 한 번만 읽는다.
        Map<ConditionGroup, List<PriceTransaction>> pooledByGroup = new EnumMap<>(ConditionGroup.class);

        List<PriceValuation.ConditionValuation> conditions = new ArrayList<>();
        for (SetCondition condition : SetCondition.values()) {
            List<PriceTransaction> series = repository.findByConditionAscending(setNumber, condition);
            if (series.size() >= MIN_REAL_SAMPLES) {
                conditions.add(PriceValuation.grade(condition, marketPrice, medianPrice(series), series.size()));
                continue;
            }

            ConditionGroup group = condition.group();
            // 단독 등급 그룹(미개봉)은 그룹 표본이 등급 표본과 같아 조회할 이유가 없다.
            if (group.members().size() > 1) {
                List<PriceTransaction> pooled = pooledByGroup.computeIfAbsent(
                        group, g -> repository.findByConditionsAscending(setNumber, g.members()));
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
