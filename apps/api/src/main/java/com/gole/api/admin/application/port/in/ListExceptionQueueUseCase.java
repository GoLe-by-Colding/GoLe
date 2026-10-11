package com.gole.api.admin.application.port.in;

import java.time.Instant;
import java.util.List;

/**
 * Inbound port: 운영 예외 큐(분쟁·분쟁 판정 지연·택배사 미접수·배송 정체·추적 불가). 큐는 주문·배송의 현재 상태에서 매번 계산한다.
 */
public interface ListExceptionQueueUseCase {

    /** 오래된 순. */
    List<ExceptionEntry> list();

    /**
     * @param shipment 배송 사실(R4.3) — 분쟁 판정 근거로 화면에 함께 보여준다. 미발송이면 null.
     */
    record ExceptionEntry(
            String type,
            String typeLabel,
            String orderId,
            String orderStatus,
            String buyerId,
            String sellerId,
            long amount,
            Instant since,
            String reason,
            String disputeDetail,
            ShipmentFacts shipment) {}

    record ShipmentFacts(
            String carrierLabel,
            String waybillNumber,
            String status,
            String rawStatus,
            Instant registeredAt,
            Instant deliveredAt,
            Instant lastTrackedAt) {}
}
