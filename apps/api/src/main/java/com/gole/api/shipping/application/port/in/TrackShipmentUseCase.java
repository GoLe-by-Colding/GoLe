package com.gole.api.shipping.application.port.in;

import com.gole.api.shipping.domain.model.Shipment;

/**
 * Inbound port: 배송 상태 갱신(트래커 조회 후 반영). (R2)
 * 폴링 스케줄러와 수동 새로고침이 함께 쓴다.
 */
public interface TrackShipmentUseCase {

    Shipment track(String orderId);
}
