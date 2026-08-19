package com.gole.api.shipping.application.service;

import com.gole.api.shipping.application.port.in.TrackShipmentUseCase;
import com.gole.api.shipping.application.port.out.ShipmentRepositoryPort;
import com.gole.api.shipping.domain.model.Shipment;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 배송 상태 폴링. (R2.3)
 *
 * <p>추적이 끝나지 않은 배송을 주기적으로 갱신한다. 조회 실패는 트래커 어댑터가
 * {@code UNKNOWN}으로 접으므로 여기서는 저장 충돌 같은 예외만 건별로 격리한다(R7.4).
 */
@Component
public class ShipmentTrackingScheduler {

    private static final Logger log = LoggerFactory.getLogger(ShipmentTrackingScheduler.class);
    private static final int BATCH_LIMIT = 100;

    private final ShipmentRepositoryPort shipments;
    private final TrackShipmentUseCase trackShipment;

    public ShipmentTrackingScheduler(ShipmentRepositoryPort shipments, TrackShipmentUseCase trackShipment) {
        this.shipments = shipments;
        this.trackShipment = trackShipment;
    }

    @Scheduled(
            initialDelayString = "${shipping.tracker.poll-initial-delay:PT30S}",
            fixedDelayString = "${shipping.tracker.poll-interval:PT1M}")
    public void poll() {
        List<Shipment> candidates = shipments.findTrackable(BATCH_LIMIT);
        for (Shipment shipment : candidates) {
            try {
                trackShipment.track(shipment.getOrderId());
            } catch (RuntimeException e) {
                log.warn("[shipment poll] orderId={} failed: {}", shipment.getOrderId(), e.getMessage());
            }
        }
    }
}
