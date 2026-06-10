package com.gole.api.order.application.port.out;

import java.time.Instant;

/**
 * Outbound port: 가격(pricing) 컨텍스트로의 체결가 기록. 주문 완료 시 호출된다.
 * (요구사항 9.1)
 */
public interface ExecutedPriceRecorderPort {

    /** 상품 상태 포함(신규). */
    void record(String setNumber, long price, int quantity, Instant executedAt, String condition);

    /** 하위호환 — 상태 미지정(미개봉 기본). */
    default void record(String setNumber, long price, int quantity, Instant executedAt) {
        record(setNumber, price, quantity, executedAt, null);
    }
}
