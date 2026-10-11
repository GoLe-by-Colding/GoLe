package com.gole.api.order.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Outbound port: 주문 파이프라인 규칙이 보는 배송 사실. 배송 도메인 객체 대신 주문 id 와 필요한 값만 받는다. */
public interface PipelineShipmentPort {

    /** 배송 완료 시각이 {@code before} 이전인 주문 id. */
    List<String> orderIdsDeliveredBefore(Instant before);

    /** 송장 등록 뒤 택배사가 접수하지 않은 주문 id(등록 시각이 {@code before} 이전). */
    List<String> orderIdsAwaitingPickupRegisteredBefore(Instant before);

    /** 배송 중 상태가 {@code since} 이후로 바뀌지 않은 주문 id. */
    List<String> orderIdsInTransitStalledSince(Instant since);

    /** 추적 불가가 {@code since} 이전부터 이어진 주문 id. */
    List<String> orderIdsTrackerUnknownSince(Instant since);

    boolean hasShipment(String orderId);

    Optional<ShipmentContact> contactOf(String orderId);

    /** 미접수 알림에 쓰는 판매자와 택배사 표시 이름. */
    record ShipmentContact(String sellerId, String carrierLabel) {}
}
