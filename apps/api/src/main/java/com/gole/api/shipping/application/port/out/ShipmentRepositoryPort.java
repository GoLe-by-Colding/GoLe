package com.gole.api.shipping.application.port.out;

import com.gole.api.shipping.domain.model.DeliveryStatus;
import com.gole.api.shipping.domain.model.Shipment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port: 배송 영속화.
 */
public interface ShipmentRepositoryPort {

    Shipment save(Shipment shipment);

    Optional<Shipment> findByOrderId(String orderId);

    /** 추적이 아직 끝나지 않은 배송(폴링 대상). 오래 조회 안 된 것부터. */
    List<Shipment> findTrackable(int limit);

    List<Shipment> findByStatusAndDeliveredAtBefore(DeliveryStatus status, Instant cutoff);

    List<Shipment> findByStatusAndRegisteredAtBefore(DeliveryStatus status, Instant cutoff);

    List<Shipment> findByStatusAndStatusChangedAtBefore(DeliveryStatus status, Instant cutoff);

    List<Shipment> findByUnknownSinceBefore(Instant cutoff);
}
