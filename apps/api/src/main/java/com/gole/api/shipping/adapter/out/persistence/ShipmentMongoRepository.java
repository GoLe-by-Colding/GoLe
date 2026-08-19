package com.gole.api.shipping.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

/** 배송 Spring Data MongoDB 리포지토리. */
public interface ShipmentMongoRepository extends MongoRepository<ShipmentDocument, String> {

    Optional<ShipmentDocument> findByOrderId(String orderId);

    /** 추적 미종결(폴링 대상). 오래 조회 안 된 것부터 — null(미조회)이 가장 앞에 온다. */
    List<ShipmentDocument> findTop100ByStatusInOrderByLastTrackedAtAsc(List<String> statuses);

    List<ShipmentDocument> findTop100ByStatusAndDeliveredAtBefore(String status, Instant cutoff);

    List<ShipmentDocument> findTop100ByStatusAndRegisteredAtBefore(String status, Instant cutoff);

    List<ShipmentDocument> findTop100ByStatusAndStatusChangedAtBefore(String status, Instant cutoff);

    List<ShipmentDocument> findTop100ByUnknownSinceBefore(Instant cutoff);
}
