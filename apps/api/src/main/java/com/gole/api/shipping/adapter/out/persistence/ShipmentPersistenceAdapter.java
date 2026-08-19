package com.gole.api.shipping.adapter.out.persistence;

import com.gole.api.shipping.adapter.out.persistence.ShipmentDocument.WaybillChangeDocument;
import com.gole.api.shipping.application.port.out.ShipmentRepositoryPort;
import com.gole.api.shipping.domain.model.Carrier;
import com.gole.api.shipping.domain.model.DeliveryStatus;
import com.gole.api.shipping.domain.model.Shipment;
import com.gole.api.shipping.domain.model.WaybillChange;
import com.gole.api.shipping.domain.model.WaybillNumber;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 배송 영속성 어댑터. 도메인 ↔ 도큐먼트 매핑. */
@Component
public class ShipmentPersistenceAdapter implements ShipmentRepositoryPort {

    private final ShipmentMongoRepository repository;

    public ShipmentPersistenceAdapter(ShipmentMongoRepository repository) {
        this.repository = repository;
    }

    @Override
    public Shipment save(Shipment shipment) {
        return toDomain(repository.save(toDocument(shipment)));
    }

    @Override
    public Optional<Shipment> findByOrderId(String orderId) {
        return repository.findByOrderId(orderId).map(ShipmentPersistenceAdapter::toDomain);
    }

    @Override
    public List<Shipment> findTrackable(int limit) {
        return repository
                .findTop100ByStatusInOrderByLastTrackedAtAsc(
                        List.of(DeliveryStatus.PENDING.name(), DeliveryStatus.IN_TRANSIT.name()))
                .stream()
                .limit(limit)
                .map(ShipmentPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public List<Shipment> findByStatusAndDeliveredAtBefore(DeliveryStatus status, Instant cutoff) {
        return repository.findTop100ByStatusAndDeliveredAtBefore(status.name(), cutoff).stream()
                .map(ShipmentPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public List<Shipment> findByStatusAndRegisteredAtBefore(DeliveryStatus status, Instant cutoff) {
        return repository.findTop100ByStatusAndRegisteredAtBefore(status.name(), cutoff).stream()
                .map(ShipmentPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public List<Shipment> findByStatusAndStatusChangedAtBefore(DeliveryStatus status, Instant cutoff) {
        return repository.findTop100ByStatusAndStatusChangedAtBefore(status.name(), cutoff).stream()
                .map(ShipmentPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public List<Shipment> findByUnknownSinceBefore(Instant cutoff) {
        return repository.findTop100ByUnknownSinceBefore(cutoff).stream()
                .map(ShipmentPersistenceAdapter::toDomain)
                .toList();
    }

    private static ShipmentDocument toDocument(Shipment shipment) {
        return new ShipmentDocument(
                shipment.getId(),
                shipment.getOrderId(),
                shipment.getSellerId(),
                shipment.getBuyerId(),
                shipment.getSellerPhone(),
                shipment.getCarrier().name(),
                shipment.getWaybill().value(),
                shipment.getStatus().name(),
                shipment.getRawStatus(),
                shipment.getRegisteredAt(),
                shipment.getStatusChangedAt(),
                shipment.getDeliveredAt(),
                shipment.getLastTrackedAt(),
                shipment.getUnknownSince(),
                shipment.getHistory().stream()
                        .map(h -> new WaybillChangeDocument(h.carrier().name(), h.waybillNumber(), h.replacedAt()))
                        .toList(),
                shipment.getVersion());
    }

    private static Shipment toDomain(ShipmentDocument doc) {
        List<WaybillChange> history = doc.getHistory() == null
                ? List.of()
                : doc.getHistory().stream()
                        .map(h -> new WaybillChange(
                                Carrier.valueOf(h.getCarrier()), h.getWaybillNumber(), h.getReplacedAt()))
                        .toList();
        return new Shipment(
                doc.getId(),
                doc.getOrderId(),
                doc.getSellerId(),
                doc.getBuyerId(),
                doc.getSellerPhone(),
                Carrier.valueOf(doc.getCarrier()),
                new WaybillNumber(doc.getWaybillNumber()),
                DeliveryStatus.valueOf(doc.getStatus()),
                doc.getRawStatus(),
                doc.getRegisteredAt(),
                doc.getStatusChangedAt(),
                doc.getDeliveredAt(),
                doc.getLastTrackedAt(),
                doc.getUnknownSince(),
                history,
                doc.getVersion());
    }
}
