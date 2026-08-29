package com.gole.api.order.application.port.out;

import com.gole.api.order.domain.model.PaymentEvidenceKind;
import java.time.Instant;

/**
 * Outbound port: 가격(pricing) 컨텍스트로의 체결가 기록. 주문 완료 시 호출된다.
 * (요구사항 9.1)
 */
public interface ExecutedPriceRecorderPort {

    /** 주문 ID를 출처 참조로 함께 남기는 플랫폼 결제 체결. */
    void record(
            String orderId,
            String setNumber,
            long price,
            int quantity,
            Instant executedAt,
            String condition,
            PaymentEvidenceKind paymentEvidenceKind);
}
