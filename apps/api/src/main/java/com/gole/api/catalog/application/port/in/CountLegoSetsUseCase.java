package com.gole.api.catalog.application.port.in;

/** Inbound port: 운영 대시보드에 보일 전체 세트 수. */
public interface CountLegoSetsUseCase {

    /** 전체 세트 수(추정치 — 컬렉션 메타데이터로 센다). */
    long estimatedLegoSetCount();
}
