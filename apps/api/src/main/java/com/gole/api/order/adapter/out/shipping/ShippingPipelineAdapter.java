package com.gole.api.order.adapter.out.shipping;

import com.gole.api.order.application.port.out.PipelineShipmentPort;
import com.gole.api.shipping.application.port.in.GetShipmentUseCase;
import com.gole.api.shipping.domain.model.Shipment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 배송 컨텍스트 통합 어댑터. 배송 도메인 객체를 파이프라인 규칙에 필요한 주문 id·연락 값으로 환원한다. */
@Component
public class ShippingPipelineAdapter implements PipelineShipmentPort {

    private final GetShipmentUseCase shipments;

    public ShippingPipelineAdapter(GetShipmentUseCase shipments) {
        this.shipments = shipments;
    }

    @Override
    public List<String> orderIdsDeliveredBefore(Instant before) {
        return orderIds(shipments.findDeliveredBefore(before));
    }

    @Override
    public List<String> orderIdsAwaitingPickupRegisteredBefore(Instant before) {
        return orderIds(shipments.findPendingRegisteredBefore(before));
    }

    @Override
    public List<String> orderIdsInTransitStalledSince(Instant since) {
        return orderIds(shipments.findInTransitStalledSince(since));
    }

    @Override
    public List<String> orderIdsTrackerUnknownSince(Instant since) {
        return orderIds(shipments.findUnknownSince(since));
    }

    @Override
    public boolean hasShipment(String orderId) {
        return shipments.getByOrderId(orderId).isPresent();
    }

    @Override
    public Optional<ShipmentContact> contactOf(String orderId) {
        return shipments
                .getByOrderId(orderId)
                .map(shipment -> new ShipmentContact(
                        shipment.getSellerId(), shipment.getCarrier().label()));
    }

    private static List<String> orderIds(List<Shipment> found) {
        return found.stream().map(Shipment::getOrderId).toList();
    }
}
