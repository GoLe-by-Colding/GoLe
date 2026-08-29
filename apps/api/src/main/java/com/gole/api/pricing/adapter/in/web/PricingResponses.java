package com.gole.api.pricing.adapter.in.web;

import com.gole.api.pricing.domain.model.PriceSnapshot;
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

    public record PricePointResponse(long price, int quantity, Instant executedAt, String source, String condition) {

        public static PricePointResponse from(PriceTransaction tx) {
            return new PricePointResponse(
                    tx.price(),
                    tx.quantity(),
                    tx.executedAt(),
                    tx.source().key(),
                    tx.condition().key());
        }
    }

    public record ProvenanceResponse(String mode, List<String> includedSources, boolean demo) {}

    public record SnapshotResponse(
            String setNumber,
            String state,
            int minimumSamples,
            int sampleCount,
            List<PricePointResponse> observations,
            StatisticsResponse statistics,
            ValuationResponse valuation,
            ProvenanceResponse provenance) {

        public static SnapshotResponse from(PriceSnapshot snapshot) {
            return new SnapshotResponse(
                    snapshot.setNumber(),
                    snapshot.state().name(),
                    snapshot.minimumSamples(),
                    snapshot.sampleCount(),
                    snapshot.observations().stream()
                            .map(PricePointResponse::from)
                            .toList(),
                    snapshot.statistics() == null ? null : StatisticsResponse.of(snapshot.statistics()),
                    snapshot.valuation() == null ? null : ValuationResponse.of(snapshot.valuation()),
                    new ProvenanceResponse(
                            provenanceMode(snapshot),
                            snapshot.includedSources().stream()
                                    .map(source -> source.key())
                                    .sorted()
                                    .toList(),
                            snapshot.demo()));
        }

        private static String provenanceMode(PriceSnapshot snapshot) {
            if (snapshot.includedSources().isEmpty()) {
                return "NONE";
            }
            if (snapshot.includedSources().size() > 1) {
                return "MIXED";
            }
            return switch (snapshot.includedSources().iterator().next()) {
                case PLATFORM_PAYMENT -> "FIRST_PARTY";
                case PLATFORM_TEST -> "DEMO";
                case DIRECT_TRADE -> "DIRECT_TRADE";
                case DEMO_SEED -> "DEMO";
                case LEGACY_UNVERIFIED -> "LEGACY_UNVERIFIED";
            };
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
