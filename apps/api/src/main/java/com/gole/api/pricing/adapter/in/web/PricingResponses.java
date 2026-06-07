package com.gole.api.pricing.adapter.in.web;

import com.gole.api.pricing.domain.model.PriceStatistics;
import com.gole.api.pricing.domain.model.PriceTransaction;
import java.time.Instant;

public final class PricingResponses {

    private PricingResponses() {
    }

    public record StatisticsResponse(
            String setNumber,
            boolean hasData,
            Long latestPrice,
            Long highestPrice,
            Long lowestPrice,
            int transactionCount) {

        public static StatisticsResponse noData(String setNumber) {
            return new StatisticsResponse(setNumber, false, null, null, null, 0);
        }

        public static StatisticsResponse of(PriceStatistics stats) {
            return new StatisticsResponse(
                    stats.setNumber(),
                    true,
                    stats.latestPrice(),
                    stats.highestPrice(),
                    stats.lowestPrice(),
                    stats.transactionCount());
        }
    }

    public record PricePointResponse(long price, int quantity, Instant executedAt) {

        public static PricePointResponse from(PriceTransaction tx) {
            return new PricePointResponse(tx.price(), tx.quantity(), tx.executedAt());
        }
    }
}
