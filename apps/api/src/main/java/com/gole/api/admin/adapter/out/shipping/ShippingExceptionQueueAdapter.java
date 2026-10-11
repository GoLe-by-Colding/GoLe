package com.gole.api.admin.adapter.out.shipping;

import com.gole.api.admin.application.port.in.ListExceptionQueueUseCase.ShipmentFacts;
import com.gole.api.admin.application.port.out.ExceptionQueueShipmentPort;
import com.gole.api.shipping.application.port.in.GetShipmentUseCase;
import com.gole.api.shipping.domain.model.Shipment;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 배송 컨텍스트 통합 어댑터. 배송 도메인 객체를 예외 큐에 필요한 사실로 환원한다. */
@Component
public class ShippingExceptionQueueAdapter implements ExceptionQueueShipmentPort {

    private final GetShipmentUseCase shipments;

    public ShippingExceptionQueueAdapter(GetShipmentUseCase shipments) {
        this.shipments = shipments;
    }

    @Override
    public List<QueueShipment> awaitingPickupRegisteredBefore(Instant before) {
        return toQueue(shipments.findPendingRegisteredBefore(before));
    }

    @Override
    public List<QueueShipment> inTransitStalledSince(Instant since) {
        return toQueue(shipments.findInTransitStalledSince(since));
    }

    @Override
    public List<QueueShipment> trackerUnknownSince(Instant since) {
        return toQueue(shipments.findUnknownSince(since));
    }

    @Override
    public Optional<ShipmentFacts> factsOf(String orderId) {
        return shipments.getByOrderId(orderId).map(ShippingExceptionQueueAdapter::toFacts);
    }

    private static List<QueueShipment> toQueue(List<Shipment> found) {
        return found.stream()
                .map(s -> new QueueShipment(
                        s.getOrderId(), s.getRegisteredAt(), s.getStatusChangedAt(), s.getUnknownSince()))
                .toList();
    }

    private static ShipmentFacts toFacts(Shipment s) {
        return new ShipmentFacts(
                s.getCarrier().label(),
                s.getWaybill().value(),
                s.getStatus().name().toLowerCase(Locale.ROOT),
                s.getRawStatus(),
                s.getRegisteredAt(),
                s.getDeliveredAt(),
                s.getLastTrackedAt());
    }
}
