package com.gole.api.admin.application.service;

import com.gole.api.order.application.port.in.GetOrderUseCase;
import com.gole.api.order.application.port.out.OrderRepositoryPort;
import com.gole.api.order.application.service.pipeline.PipelineProperties;
import com.gole.api.order.domain.model.Order;
import com.gole.api.order.domain.model.OrderStatus;
import com.gole.api.shipping.application.port.in.GetShipmentUseCase;
import com.gole.api.shipping.domain.model.Shipment;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * 예외 큐 계산. (shipping-and-fees R7.6, Q1/Q2)
 *
 * <p>별도 컬렉션이 없다 — 큐 멤버십은 주문·배송의 <b>현재 상태에서 매번 계산</b>한다.
 * 따로 적재하면 실제 상태와 어긋난 "유령 예외"가 생기고, 그걸 지우는 운영이 또 생긴다.
 * 등재 시점 1회 알림은 파이프라인 규칙(마커)이 따로 담당한다.
 */
@Service
public class ExceptionQueueService {

    private final OrderRepositoryPort orders;
    private final GetOrderUseCase getOrder;
    private final GetShipmentUseCase shipments;
    private final PipelineProperties properties;
    private final Clock clock;

    public ExceptionQueueService(
            OrderRepositoryPort orders,
            GetOrderUseCase getOrder,
            GetShipmentUseCase shipments,
            PipelineProperties properties,
            Clock clock) {
        this.orders = orders;
        this.getOrder = getOrder;
        this.shipments = shipments;
        this.properties = properties;
        this.clock = clock;
    }

    public List<ExceptionEntry> list() {
        Instant now = Instant.now(clock);
        List<ExceptionEntry> entries = new ArrayList<>();

        // 분쟁(즉시) + 판정 지연(3일 초과 시 에스컬레이션 표시)
        for (Order order : orders.findByStatus(OrderStatus.DISPUTED)) {
            boolean escalated = order.getStatusChangedAt().isBefore(now.minus(properties.disputeEscalationAfter()));
            entries.add(entry(
                    escalated ? "dispute_escalated" : "dispute",
                    escalated ? "분쟁 판정 지연" : "분쟁",
                    order,
                    order.getDisputeOpenedAt() == null ? order.getStatusChangedAt() : order.getDisputeOpenedAt(),
                    order.getDisputeReason() == null
                            ? null
                            : order.getDisputeReason().label()));
        }
        // 택배사 미접수(3일)
        for (Shipment s : shipments.findPendingRegisteredBefore(now.minus(properties.carrierPickupTimeout()))) {
            addShipmentEntry(entries, "carrier_pickup_stall", "택배사 미접수", s, s.getRegisteredAt());
        }
        // 배송 정체(14일)
        for (Shipment s : shipments.findInTransitStalledSince(now.minus(properties.transitStallAfter()))) {
            addShipmentEntry(entries, "transit_stall", "배송 정체", s, s.getStatusChangedAt());
        }
        // 추적 불가(24시간)
        for (Shipment s : shipments.findUnknownSince(now.minus(properties.trackerUnknownAfter()))) {
            addShipmentEntry(entries, "tracker_unknown", "추적 불가", s, s.getUnknownSince());
        }

        entries.sort(Comparator.comparing(ExceptionEntry::since));
        return entries;
    }

    private void addShipmentEntry(
            List<ExceptionEntry> entries, String type, String label, Shipment shipment, Instant since) {
        Order order;
        try {
            order = getOrder.getById(shipment.getOrderId());
        } catch (RuntimeException missing) {
            return; // 주문이 사라진 배송 — 큐에 올릴 수 없다
        }
        // 이미 종결(환불·완료)된 주문의 배송 문제는 사람이 볼 일이 아니다.
        if (order.getStatus() != OrderStatus.FUNDS_HELD && order.getStatus() != OrderStatus.DISPUTED) {
            return;
        }
        entries.add(entry(type, label, order, since, null));
    }

    private ExceptionEntry entry(String type, String label, Order order, Instant since, String detail) {
        ShipmentFacts facts =
                shipments.getByOrderId(order.getId()).map(ShipmentFacts::from).orElse(null);
        return new ExceptionEntry(
                type,
                label,
                order.getId(),
                order.getStatus().name().toLowerCase(Locale.ROOT),
                order.getBuyerId(),
                order.getSellerId(),
                order.getAmount(),
                since,
                detail,
                order.getDisputeDetail(),
                facts);
    }

    /**
     * @param shipment 배송 사실(R4.3) — 분쟁 판정 근거로 화면에 함께 보여준다. 미발송이면 null.
     */
    public record ExceptionEntry(
            String type,
            String typeLabel,
            String orderId,
            String orderStatus,
            String buyerId,
            String sellerId,
            long amount,
            Instant since,
            String reason,
            String disputeDetail,
            ShipmentFacts shipment) {}

    public record ShipmentFacts(
            String carrierLabel,
            String waybillNumber,
            String status,
            String rawStatus,
            Instant registeredAt,
            Instant deliveredAt,
            Instant lastTrackedAt) {

        static ShipmentFacts from(Shipment s) {
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
}
