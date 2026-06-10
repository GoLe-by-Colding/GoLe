package com.gole.api.pricing.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.pricing.application.port.in.RecordExecutedPriceUseCase.RecordExecutedPriceCommand;
import com.gole.api.pricing.application.port.out.PriceTransactionRepositoryPort;
import com.gole.api.pricing.application.port.out.PriceTransactionRepositoryPort.TradeAggregate;
import com.gole.api.pricing.domain.model.PriceStatistics;
import com.gole.api.pricing.domain.model.PriceTransaction;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PricingServiceTest {

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
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        service.record(new RecordExecutedPriceCommand("10307", 100, 1, base));
        service.record(new RecordExecutedPriceCommand("10307", 300, 1, base.plus(1, ChronoUnit.DAYS)));
        service.record(new RecordExecutedPriceCommand("10307", 200, 1, base.plus(2, ChronoUnit.DAYS)));

        PriceStatistics stats = service.getStatistics("10307", null, null).orElseThrow();
        assertThat(stats.latestPrice()).isEqualTo(200); // 가장 최근
        assertThat(stats.highestPrice()).isEqualTo(300);
        assertThat(stats.lowestPrice()).isEqualTo(100);
        assertThat(stats.transactionCount()).isEqualTo(3);
    }

    @Test
    void chart_isAscending_history_isDescending() {
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        service.record(new RecordExecutedPriceCommand("10307", 100, 1, base));
        service.record(new RecordExecutedPriceCommand("10307", 200, 1, base.plus(1, ChronoUnit.DAYS)));

        assertThat(service.getChart("10307", null, null))
                .extracting(PriceTransaction::price).containsExactly(100L, 200L);
        assertThat(service.getHistory("10307"))
                .extracting(PriceTransaction::price).containsExactly(200L, 100L);
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
        public List<PriceTransaction> findByConditionAscending(
                String setNumber, com.gole.api.pricing.domain.model.SetCondition condition) {
            return store.stream()
                    .filter(t -> t.setNumber().equals(setNumber))
                    .filter(t -> t.condition() == condition)
                    .sorted(Comparator.comparing(PriceTransaction::executedAt))
                    .toList();
        }

        @Override
        public List<TradeAggregate> findTopTradedSets(int limit, java.time.Instant since) {
            return List.of();
        }
    }
}
