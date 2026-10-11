package com.gole.api.admin.application.port.out;

import com.gole.api.admin.application.port.in.ListExceptionQueueUseCase.ShipmentFacts;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Outbound port: 예외 큐 계산에 쓰는 배송 사실. */
public interface ExceptionQueueShipmentPort {

    /** 송장 등록 뒤 택배사가 접수하지 않은 배송(등록 시각이 {@code before} 이전). */
    List<QueueShipment> awaitingPickupRegisteredBefore(Instant before);

    /** 배송 중 상태가 {@code since} 이후로 바뀌지 않은 배송. */
    List<QueueShipment> inTransitStalledSince(Instant since);

    /** 추적 불가가 {@code since} 이전부터 이어진 배송. */
    List<QueueShipment> trackerUnknownSince(Instant since);

    Optional<ShipmentFacts> factsOf(String orderId);

    record QueueShipment(String orderId, Instant registeredAt, Instant statusChangedAt, Instant unknownSince) {}
}
