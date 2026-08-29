package com.gole.api.pricing.domain.model;

import java.util.List;
import java.util.Set;

/** 한 세트의 체결 관측·신뢰 단계·통계·밸류에이션을 같은 표본 계약으로 묶은 읽기 모델. */
public record PriceSnapshot(
        String setNumber,
        MarketDataState state,
        int minimumSamples,
        int sampleCount,
        List<PriceTransaction> observations,
        PriceStatistics statistics,
        PriceValuation valuation,
        Set<PriceTransactionSource> includedSources,
        boolean demo) {}
