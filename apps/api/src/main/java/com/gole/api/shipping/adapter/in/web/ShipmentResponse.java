package com.gole.api.shipping.adapter.in.web;

import com.gole.api.shipping.domain.model.Shipment;
import com.gole.api.shipping.domain.model.WaybillChange;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * 배송 응답 DTO.
 *
 * <p>판매자 연락처는 <b>포함하지 않는다</b> — 연락처는 마스킹 기본 원칙(R8.4)에 따라
 * 전용 연락처 엔드포인트({@code /orders/{id}/contacts})로만 제공한다.
 */
public record ShipmentResponse(
        String orderId,
        String carrier,
        String carrierLabel,
        String waybillNumber,
        String status,
        String rawStatus,
        Instant registeredAt,
        Instant deliveredAt,
        Instant lastTrackedAt,
        List<WaybillChangeResponse> history) {

    public static ShipmentResponse from(Shipment shipment) {
        return new ShipmentResponse(
                shipment.getOrderId(),
                shipment.getCarrier().key(),
                shipment.getCarrier().label(),
                shipment.getWaybill().value(),
                shipment.getStatus().name().toLowerCase(Locale.ROOT),
                shipment.getRawStatus(),
                shipment.getRegisteredAt(),
                shipment.getDeliveredAt(),
                shipment.getLastTrackedAt(),
                shipment.getHistory().stream().map(WaybillChangeResponse::from).toList());
    }

    public record WaybillChangeResponse(String carrier, String carrierLabel, String waybillNumber, Instant replacedAt) {

        static WaybillChangeResponse from(WaybillChange change) {
            return new WaybillChangeResponse(
                    change.carrier().key(), change.carrier().label(), change.waybillNumber(), change.replacedAt());
        }
    }
}
