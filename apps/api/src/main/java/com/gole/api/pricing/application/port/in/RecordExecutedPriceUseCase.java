package com.gole.api.pricing.application.port.in;

import java.time.Instant;

/**
 * Inbound port: 체결가 기록. (요구사항 9.1)
 * 추후 Order 완료 시 Settlement/Trade가 호출한다.
 */
public interface RecordExecutedPriceUseCase {

    void record(RecordExecutedPriceCommand command);

    record RecordExecutedPriceCommand(
            String setNumber, long price, int quantity, Instant executedAt, String condition) {

        /** 상태 미지정 체결(레거시/주문 기본) — 미개봉으로 간주. */
        public RecordExecutedPriceCommand(
                String setNumber, long price, int quantity, Instant executedAt) {
            this(setNumber, price, quantity, executedAt, null);
        }
    }
}
