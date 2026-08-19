package com.gole.api.shipping.application.port.in;

import com.gole.api.shipping.domain.model.Shipment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Inbound port: 배송 조회. 다른 컨텍스트(order 파이프라인·admin 예외 큐)가
 * 이 포트로만 shipping을 참조한다(NFR-3).
 */
public interface GetShipmentUseCase {

    Optional<Shipment> getByOrderId(String orderId);

    /** 배송 완료 후 cutoff 이전에 완료된 건 — 자동 구매확정 후보. (R3.2) */
    List<Shipment> findDeliveredBefore(Instant cutoff);

    /** 송장 등록 후 cutoff까지 택배사 미접수(PENDING) — 예외 큐 후보. (R9 미접수) */
    List<Shipment> findPendingRegisteredBefore(Instant cutoff);

    /** cutoff 이전부터 이동중 상태 그대로 — 배송 정체 후보. (R9 배송정체) */
    List<Shipment> findInTransitStalledSince(Instant cutoff);

    /** cutoff 이전부터 연속 조회 불가 — 추적 불가 후보. (R9 추적불가) */
    List<Shipment> findUnknownSince(Instant cutoff);
}
