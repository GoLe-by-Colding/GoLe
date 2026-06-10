package com.gole.api.pricing.adapter.in.web;

import com.gole.api.pricing.domain.model.PriceStatistics;
import com.gole.api.pricing.domain.model.PriceTransaction;
import com.gole.api.pricing.domain.model.PriceValuation;
import java.time.Instant;
import java.util.List;

public final class PricingResponses {

    private PricingResponses() {}

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

    public record ConditionValuationResponse(
            String condition,
            int depreciationPct,
            long fairPrice,
            long sellPrice,
            long buyPrice,
            int sampleCount,
            boolean basedOnRealData) {

        public static ConditionValuationResponse from(PriceValuation.ConditionValuation c) {
            return new ConditionValuationResponse(
                    c.condition().key(),
                    c.depreciationPct(),
                    c.fairPrice(),
                    c.sellPrice(),
                    c.buyPrice(),
                    c.sampleCount(),
                    c.basedOnRealData());
        }
    }

    public record ValuationResponse(
            String setNumber, boolean hasData, Long marketPrice, List<ConditionValuationResponse> conditions) {

        public static ValuationResponse noData(String setNumber) {
            return new ValuationResponse(setNumber, false, null, List.of());
        }

        public static ValuationResponse of(PriceValuation valuation) {
            return new ValuationResponse(
                    valuation.setNumber(),
                    true,
                    valuation.marketPrice(),
                    valuation.conditions().stream()
                            .map(ConditionValuationResponse::from)
                            .toList());
        }
    }
}
