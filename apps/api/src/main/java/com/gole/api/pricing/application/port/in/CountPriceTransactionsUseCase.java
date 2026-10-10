package com.gole.api.pricing.application.port.in;

/** Inbound port: 운영 대시보드에 보일 전체 체결 기록 수. */
public interface CountPriceTransactionsUseCase {

    /** 전체 체결 기록 수(추정치 — 컬렉션 메타데이터로 센다). */
    long estimatedPriceTransactionCount();
}
