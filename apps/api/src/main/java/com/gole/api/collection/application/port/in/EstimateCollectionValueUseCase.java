package com.gole.api.collection.application.port.in;

import com.gole.api.collection.domain.model.CollectionValuation;

/**
 * Inbound port: 보유(owned) 항목의 추정 총가치. (요구사항 11.5, Pricing 연동)
 */
public interface EstimateCollectionValueUseCase {

    long estimateOwnedValue(String userId);

    /** 추정 총가치와 그 근거(보유 수·시세가 잡힌 수). (collection-value-history H5) */
    CollectionValuation valuate(String userId);
}
