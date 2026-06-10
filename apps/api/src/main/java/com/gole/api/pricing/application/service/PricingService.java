package com.gole.api.pricing.application.service;

import com.gole.api.pricing.application.port.in.GetPriceInsightsUseCase;
import com.gole.api.pricing.application.port.in.RecordExecutedPriceUseCase;
import com.gole.api.pricing.application.port.out.PriceTransactionRepositoryPort;
import com.gole.api.pricing.domain.model.PriceStatistics;
import com.gole.api.pricing.domain.model.PriceTransaction;
import com.gole.api.pricing.domain.model.PriceValuation;
import com.gole.api.pricing.domain.model.SetCondition;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

        List<PriceValuation.ConditionValuation> conditions = new ArrayList<>();
        for (SetCondition condition : SetCondition.values()) {
            List<PriceTransaction> series = repository.findByConditionAscending(setNumber, condition);
            if (series.size() >= MIN_REAL_SAMPLES) {
                long fair = medianPrice(series);
                conditions.add(PriceValuation.real(condition, marketPrice, fair, series.size()));
            } else {
                conditions.add(PriceValuation.model(condition, marketPrice));
            }
        }
        return Optional.of(new PriceValuation(setNumber, marketPrice, List.copyOf(conditions)));
    }

    /** 상태별 실데이터를 신뢰하기 위한 최소 표본 수. */
    private static final int MIN_REAL_SAMPLES = 3;

    private static long medianPrice(List<PriceTransaction> ascending) {
        long[] prices =
                ascending.stream().mapToLong(PriceTransaction::price).sorted().toArray();
        int n = prices.length;
        return n % 2 == 1 ? prices[n / 2] : Math.round((prices[n / 2 - 1] + prices[n / 2]) / 2.0);
    }
}
