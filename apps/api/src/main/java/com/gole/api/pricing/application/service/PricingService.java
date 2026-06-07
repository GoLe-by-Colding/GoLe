package com.gole.api.pricing.application.service;

import com.gole.api.pricing.application.port.in.GetPriceInsightsUseCase;
import com.gole.api.pricing.application.port.in.RecordExecutedPriceUseCase;
import com.gole.api.pricing.application.port.out.PriceTransactionRepositoryPort;
import com.gole.api.pricing.domain.model.PriceStatistics;
import com.gole.api.pricing.domain.model.PriceTransaction;
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
                command.setNumber(), command.price(), command.quantity(), command.executedAt()));
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
    public List<PriceTransaction> getHistory(String setNumber) {
        List<PriceTransaction> recentFirst =
                new ArrayList<>(repository.findInRangeAscending(setNumber, null, null));
        Collections.reverse(recentFirst);
        return recentFirst;
    }
}
