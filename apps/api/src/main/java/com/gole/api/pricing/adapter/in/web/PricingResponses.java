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

    /**
     * @param basis           공정가 근거: {@code grade}(등급 실측) | {@code group}(그룹 실측 환산) |
     *                        {@code model}(감가 모델)
     * @param sampleCount     공정가 산출에 실제로 쓰인 체결 건수. {@code model}이면 0
     * @param basedOnRealData {@code basis != model}. 기존 클라이언트 호환용으로 유지한다
     */
    public record ConditionValuationResponse(
            String condition,
            String basis,
            int depreciationPct,
            long fairPrice,
            long sellPrice,
            long buyPrice,
            int sampleCount,
            boolean basedOnRealData) {

        public static ConditionValuationResponse from(PriceValuation.ConditionValuation c) {
            return new ConditionValuationResponse(
                    c.condition().key(),
                    c.basis().key(),
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
