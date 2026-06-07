package com.gole.api.pricing.domain.model;

import java.util.List;

/**
 * 특정 기간 내 카탈로그 세트의 시세 통계. (요구사항 9.2)
 */
public record PriceStatistics(
        String setNumber,
        long latestPrice,
        long highestPrice,
        long lowestPrice,
        int transactionCount) {

    /** 체결 내역(최신순 정렬 가정)으로부터 통계를 계산한다. */
    public static PriceStatistics from(String setNumber, List<PriceTransaction> recentFirst) {
        if (recentFirst.isEmpty()) {
            throw new IllegalArgumentException("cannot compute statistics from empty transactions");
        }
        long latest = recentFirst.get(0).price();
        long highest = recentFirst.stream().mapToLong(PriceTransaction::price).max().orElse(latest);
        long lowest = recentFirst.stream().mapToLong(PriceTransaction::price).min().orElse(latest);
        return new PriceStatistics(setNumber, latest, highest, lowest, recentFirst.size());
    }
}
