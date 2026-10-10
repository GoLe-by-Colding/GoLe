package com.gole.api.admin.application.service;

import com.gole.api.admin.application.port.in.ListExceptionQueueUseCase;
import com.gole.api.admin.application.port.out.ExceptionQueueOrderPort;
import com.gole.api.admin.application.port.out.ExceptionQueueOrderPort.QueueOrder;
import com.gole.api.admin.application.port.out.ExceptionQueueOrderPort.Thresholds;
import com.gole.api.admin.application.port.out.ExceptionQueueShipmentPort;
import com.gole.api.admin.application.port.out.ExceptionQueueShipmentPort.QueueShipment;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.stereotype.Service;

/**
 * 예외 큐 계산. (shipping-and-fees R7.6, Q1/Q2)
 *
 * <p>별도 컬렉션이 없다 — 큐 멤버십은 주문·배송의 <b>현재 상태에서 매번 계산</b>한다.
 * 따로 적재하면 실제 상태와 어긋난 "유령 예외"가 생기고, 그걸 지우는 운영이 또 생긴다.
 * 등재 시점 1회 알림은 파이프라인 규칙(마커)이 따로 담당한다.
 */
@Service
public class ExceptionQueueService implements ListExceptionQueueUseCase {

    private final ExceptionQueueOrderPort orders;
    private final ExceptionQueueShipmentPort shipments;
    private final Clock clock;

    public ExceptionQueueService(ExceptionQueueOrderPort orders, ExceptionQueueShipmentPort shipments, Clock clock) {
        this.orders = orders;
        this.shipments = shipments;
        this.clock = clock;
    }

    @Override
    public List<ExceptionEntry> list() {
        Instant now = Instant.now(clock);
        Thresholds thresholds = orders.thresholds();
        List<ExceptionEntry> entries = new ArrayList<>();

        // 분쟁(즉시) + 판정 지연(기준 초과 시 에스컬레이션 표시)
        for (QueueOrder order : orders.disputedOrders()) {
            boolean escalated = order.statusChangedAt().isBefore(now.minus(thresholds.disputeEscalationAfter()));
            entries.add(entry(
                    escalated ? "dispute_escalated" : "dispute",
                    escalated ? "분쟁 판정 지연" : "분쟁",
                    order,
                    order.disputeOpenedAt() == null ? order.statusChangedAt() : order.disputeOpenedAt(),
                    order.disputeReasonLabel()));
        }
        // 택배사 미접수
        addShipmentEntries(
                entries,
                "carrier_pickup_stall",
                "택배사 미접수",
                shipments.awaitingPickupRegisteredBefore(now.minus(thresholds.carrierPickupTimeout())),
                QueueShipment::registeredAt);
        // 배송 정체
        addShipmentEntries(
                entries,
                "transit_stall",
                "배송 정체",
                shipments.inTransitStalledSince(now.minus(thresholds.transitStallAfter())),
                QueueShipment::statusChangedAt);
        // 추적 불가
        addShipmentEntries(
                entries,
                "tracker_unknown",
                "추적 불가",
                shipments.trackerUnknownSince(now.minus(thresholds.trackerUnknownAfter())),
                QueueShipment::unknownSince);

        entries.sort(Comparator.comparing(ExceptionEntry::since));
        return entries;
    }

    private void addShipmentEntries(
            List<ExceptionEntry> entries,
            String type,
            String label,
            List<QueueShipment> found,
            Function<QueueShipment, Instant> since) {
        for (QueueShipment shipment : found) {
            Optional<QueueOrder> order = orders.findOrder(shipment.orderId());
            // 주문이 사라진 배송은 큐에 올릴 수 없고, 이미 종결(환불·완료)된 주문의 배송 문제는 사람이 볼 일이 아니다.
            if (order.isEmpty() || !order.get().fundsHeld()) {
                continue;
            }
            entries.add(entry(type, label, order.get(), since.apply(shipment), null));
        }
    }

    private ExceptionEntry entry(String type, String label, QueueOrder order, Instant since, String detail) {
        return new ExceptionEntry(
                type,
                label,
                order.id(),
                order.status(),
                order.buyerId(),
                order.sellerId(),
                order.amount(),
                since,
                detail,
                order.disputeDetail(),
                shipments.factsOf(order.id()).orElse(null));
    }
}
