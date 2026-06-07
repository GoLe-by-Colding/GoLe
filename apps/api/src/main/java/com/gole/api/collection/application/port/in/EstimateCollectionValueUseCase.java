package com.gole.api.collection.application.port.in;

/**
 * Inbound port: 보유(owned) 항목의 추정 총가치. (요구사항 11.5, Pricing 연동)
 */
public interface EstimateCollectionValueUseCase {

    long estimateOwnedValue(String userId);
}
