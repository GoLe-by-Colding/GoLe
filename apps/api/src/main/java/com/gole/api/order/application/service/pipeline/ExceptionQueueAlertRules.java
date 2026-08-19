package com.gole.api.order.application.service.pipeline;

import com.gole.api.common.operations.OperationalEvent;
import com.gole.api.common.operations.OperationalEvent.Category;
import com.gole.api.common.operations.OperationalEvent.Level;
import com.gole.api.common.operations.OperationalEventPublisher;
import com.gole.api.order.application.port.out.OrderEventNotifierPort;
import com.gole.api.order.application.port.out.OrderRepositoryPort;
import com.gole.api.order.application.port.out.PipelineMarkerPort;
import com.gole.api.order.domain.model.Order;
import com.gole.api.order.domain.model.OrderStatus;
import com.gole.api.shipping.application.port.in.GetShipmentUseCase;
import com.gole.api.shipping.domain.model.Shipment;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 예외 큐 등재 알림 규칙 묶음. (R7.6, R9)
 *
 * <p>이 규칙들은 상태를 바꾸지 않는다 — 예외 큐 자체는 별도 컬렉션 없이 주문·배송 상태를
 * <b>조회로 계산</b>하므로(항상 실제 상태와 일치), 여기서는 "큐에 새로 올라왔다"는 사실을
 * 운영 채널·당사자에게 1회 알리는 일만 한다. 1회성은 마커가 보장한다.
 */
public final class ExceptionQueueAlertRules {

    private ExceptionQueueAlertRules() {}

    /** 택배사 미접수. (송장 등록 후 PENDING 3일 → 판매자 알림 + 운영 채널) */
    @Component
    public static class CarrierPickupStallRule implements PipelineRule {

        private final GetShipmentUseCase shipments;
        private final OrderEventNotifierPort notifier;
        private final PipelineMarkerPort markers;
        private final OperationalEventPublisher operationalEvents;
        private final PipelineProperties properties;

        public CarrierPickupStallRule(
                GetShipmentUseCase shipments,
                OrderEventNotifierPort notifier,
                PipelineMarkerPort markers,
                OperationalEventPublisher operationalEvents,
                PipelineProperties properties) {
            this.shipments = shipments;
            this.notifier = notifier;
            this.markers = markers;
            this.operationalEvents = operationalEvents;
            this.properties = properties;
        }

        @Override
        public String name() {
            return "carrier-pickup-stall";
        }

        @Override
        public List<String> candidates(Instant now) {
            return shipments.findPendingRegisteredBefore(now.minus(properties.carrierPickupTimeout())).stream()
                    .map(Shipment::getOrderId)
                    .toList();
        }

        @Override
        public boolean apply(String orderId, Instant now) {
            if (!markers.markOnce(name(), orderId)) {
                return false;
            }
            Shipment shipment = shipments.getByOrderId(orderId).orElse(null);
            if (shipment == null) {
                return false;
            }
            notifier.shipmentReminder(shipment.getSellerId(), orderId);
            operationalEvents.publish(new OperationalEvent(
                    Category.APPLICATION,
                    Level.WARNING,
                    "택배사 미접수",
                    "운송장이 등록됐지만 택배사가 접수하지 않고 있습니다. 예외 큐를 확인하세요.",
                    Map.of("주문 ID", orderId, "택배사", shipment.getCarrier().label()),
                    now));
            return true;
        }
    }

    /** 배송 정체. (IN_TRANSIT 14일 → 운영 채널) */
    @Component
    public static class TransitStallRule implements PipelineRule {

        private final GetShipmentUseCase shipments;
        private final PipelineMarkerPort markers;
        private final OperationalEventPublisher operationalEvents;
        private final PipelineProperties properties;

        public TransitStallRule(
                GetShipmentUseCase shipments,
                PipelineMarkerPort markers,
                OperationalEventPublisher operationalEvents,
                PipelineProperties properties) {
            this.shipments = shipments;
            this.markers = markers;
            this.operationalEvents = operationalEvents;
            this.properties = properties;
        }

        @Override
        public String name() {
            return "transit-stall";
        }

        @Override
        public List<String> candidates(Instant now) {
            return shipments.findInTransitStalledSince(now.minus(properties.transitStallAfter())).stream()
                    .map(Shipment::getOrderId)
                    .toList();
        }

        @Override
        public boolean apply(String orderId, Instant now) {
            if (!markers.markOnce(name(), orderId)) {
                return false;
            }
            operationalEvents.publish(new OperationalEvent(
                    Category.APPLICATION,
                    Level.WARNING,
                    "배송 정체",
                    "이동 중 상태가 오래 지속되고 있습니다. 예외 큐에서 확인하세요.",
                    Map.of("주문 ID", orderId),
                    now));
            return true;
        }
    }

    /** 추적 불가. (트래커 UNKNOWN 24시간 연속 → 운영 채널) */
    @Component
    public static class TrackerUnknownRule implements PipelineRule {

        private final GetShipmentUseCase shipments;
        private final PipelineMarkerPort markers;
        private final OperationalEventPublisher operationalEvents;
        private final PipelineProperties properties;

        public TrackerUnknownRule(
                GetShipmentUseCase shipments,
                PipelineMarkerPort markers,
                OperationalEventPublisher operationalEvents,
                PipelineProperties properties) {
            this.shipments = shipments;
            this.markers = markers;
            this.operationalEvents = operationalEvents;
            this.properties = properties;
        }

        @Override
        public String name() {
            return "tracker-unknown";
        }

        @Override
        public List<String> candidates(Instant now) {
            return shipments.findUnknownSince(now.minus(properties.trackerUnknownAfter())).stream()
                    .map(Shipment::getOrderId)
                    .toList();
        }

        @Override
        public boolean apply(String orderId, Instant now) {
            if (!markers.markOnce(name(), orderId)) {
                return false;
            }
            operationalEvents.publish(new OperationalEvent(
                    Category.APPLICATION,
                    Level.WARNING,
                    "배송 추적 불가",
                    "트래커 조회가 계속 실패하고 있습니다. 송장번호 오류일 수 있습니다.",
                    Map.of("주문 ID", orderId),
                    now));
            return true;
        }
    }

    /**
     * 분쟁 판정 지연 에스컬레이션. (DISPUTED 3일 → 운영 채널 ERROR)
     *
     * <p>자동 판정은 하지 않는다(R9.2) — 금전 귀속을 기계가 단정하면 안 되므로
     * 사람을 더 세게 부르는 것이 이 규칙의 전부다.
     */
    @Component
    public static class DisputeEscalationRule implements PipelineRule {

        private final OrderRepositoryPort orders;
        private final PipelineMarkerPort markers;
        private final OperationalEventPublisher operationalEvents;
        private final PipelineProperties properties;

        public DisputeEscalationRule(
                OrderRepositoryPort orders,
                PipelineMarkerPort markers,
                OperationalEventPublisher operationalEvents,
                PipelineProperties properties) {
            this.orders = orders;
            this.markers = markers;
            this.operationalEvents = operationalEvents;
            this.properties = properties;
        }

        @Override
        public String name() {
            return "dispute-escalation";
        }

        @Override
        public List<String> candidates(Instant now) {
            return orders
                    .findByStatusChangedBefore(OrderStatus.DISPUTED, now.minus(properties.disputeEscalationAfter()))
                    .stream()
                    .map(Order::getId)
                    .toList();
        }

        @Override
        public boolean apply(String orderId, Instant now) {
            if (!markers.markOnce(name(), orderId)) {
                return false;
            }
            operationalEvents.publish(new OperationalEvent(
                    Category.APPLICATION,
                    Level.ERROR,
                    "분쟁 판정 지연",
                    "분쟁이 기한 내 판정되지 않았습니다. 즉시 확인이 필요합니다.",
                    Map.of("주문 ID", orderId),
                    now));
            return true;
        }
    }
}
