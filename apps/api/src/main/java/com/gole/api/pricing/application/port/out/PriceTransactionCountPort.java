package com.gole.api.pricing.application.port.out;

/** Outbound port: 전체 체결 기록 수(추정치). */
public interface PriceTransactionCountPort {

    long estimatedPriceTransactionCount();
}
