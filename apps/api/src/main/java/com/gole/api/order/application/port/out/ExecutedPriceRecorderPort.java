package com.gole.api.order.application.port.out;

import java.time.Instant;

/**
 * Outbound port: 가격(pricing) 컨텍스트로의 체결가 기록. 주문 완료 시 호출된다.
 * (요구사항 9.1)
 */
public interface ExecutedPriceRecorderPort {

    void record(String setNumber, long price, int quantity, Instant executedAt);
}
