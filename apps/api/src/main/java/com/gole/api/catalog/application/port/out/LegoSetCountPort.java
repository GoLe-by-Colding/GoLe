package com.gole.api.catalog.application.port.out;

/** Outbound port: 전체 세트 수(추정치). */
public interface LegoSetCountPort {

    long estimatedLegoSetCount();
}
