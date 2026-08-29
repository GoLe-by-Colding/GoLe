package com.gole.api.pricing.domain.model;

/** 검증된 체결 표본 수에 따른 공개 시세 단계. */
public enum MarketDataState {
    EMPTY,
    OBSERVATIONS_ONLY,
    ESTABLISHED;

    public static MarketDataState fromSampleCount(int sampleCount, int minimumSamples) {
        if (sampleCount <= 0) {
            return EMPTY;
        }
        return sampleCount < minimumSamples ? OBSERVATIONS_ONLY : ESTABLISHED;
    }
}
