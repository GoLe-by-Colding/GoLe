package com.gole.api.admin.application.port.out;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Outbound port: 예외 큐 계산에 쓰는 주문 사실과 파이프라인 기준 시간. */
public interface ExceptionQueueOrderPort {

    List<QueueOrder> disputedOrders();

    /** 주문이 없으면 비어 있다. */
    Optional<QueueOrder> findOrder(String orderId);

    Thresholds thresholds();

    /**
     * @param status 주문 상태(소문자)
     * @param fundsHeld 결제금이 아직 보관 중인가(자금 보유·분쟁). 종결된 주문의 배송 문제는 큐에 올리지 않는다.
     */
    record QueueOrder(
            String id,
            String status,
            boolean fundsHeld,
            String buyerId,
            String sellerId,
            long amount,
            Instant statusChangedAt,
            Instant disputeOpenedAt,
            String disputeReasonLabel,
            String disputeDetail) {}

    record Thresholds(
            Duration disputeEscalationAfter,
            Duration carrierPickupTimeout,
            Duration transitStallAfter,
            Duration trackerUnknownAfter) {}
}
