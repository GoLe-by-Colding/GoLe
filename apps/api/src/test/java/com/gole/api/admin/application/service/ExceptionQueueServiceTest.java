package com.gole.api.admin.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gole.api.admin.application.port.in.ListExceptionQueueUseCase.ExceptionEntry;
import com.gole.api.admin.application.port.in.ListExceptionQueueUseCase.ShipmentFacts;
import com.gole.api.admin.application.port.out.ExceptionQueueOrderPort;
import com.gole.api.admin.application.port.out.ExceptionQueueOrderPort.QueueOrder;
import com.gole.api.admin.application.port.out.ExceptionQueueOrderPort.Thresholds;
import com.gole.api.admin.application.port.out.ExceptionQueueShipmentPort;
import com.gole.api.admin.application.port.out.ExceptionQueueShipmentPort.QueueShipment;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExceptionQueueServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-10T00:00:00Z");

    private final ExceptionQueueOrderPort orders = mock(ExceptionQueueOrderPort.class);
    private final ExceptionQueueShipmentPort shipments = mock(ExceptionQueueShipmentPort.class);
    private final ExceptionQueueService service =
            new ExceptionQueueService(orders, shipments, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void defaults() {
        when(orders.thresholds())
                .thenReturn(new Thresholds(
                        Duration.ofDays(3), Duration.ofDays(3), Duration.ofDays(14), Duration.ofHours(24)));
        when(orders.disputedOrders()).thenReturn(List.of());
        when(shipments.awaitingPickupRegisteredBefore(NOW.minus(Duration.ofDays(3))))
                .thenReturn(List.of());
        when(shipments.inTransitStalledSince(NOW.minus(Duration.ofDays(14)))).thenReturn(List.of());
        when(shipments.trackerUnknownSince(NOW.minus(Duration.ofHours(24)))).thenReturn(List.of());
        when(shipments.factsOf(anyString())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("분쟁은 판정 기준을 넘기면 판정 지연으로 올라가고 사유와 배송 사실을 함께 싣는다")
    void disputes_escalateAfterThresholdWithFacts() {
        QueueOrder fresh = order("order-fresh", true, NOW.minus(Duration.ofDays(1)), NOW.minus(Duration.ofDays(1)));
        QueueOrder stale = order("order-stale", true, NOW.minus(Duration.ofDays(5)), null);
        when(orders.disputedOrders()).thenReturn(List.of(fresh, stale));
        ShipmentFacts facts = new ShipmentFacts("CJ대한통운", "123", "delivered", "배송완료", NOW, NOW, NOW);
        when(shipments.factsOf("order-stale")).thenReturn(Optional.of(facts));

        List<ExceptionEntry> entries = service.list();

        assertThat(entries).extracting(ExceptionEntry::orderId).containsExactly("order-stale", "order-fresh");
        assertThat(entries.get(0).type()).isEqualTo("dispute_escalated");
        assertThat(entries.get(0).since()).isEqualTo(NOW.minus(Duration.ofDays(5)));
        assertThat(entries.get(0).shipment()).isSameAs(facts);
        assertThat(entries.get(1).type()).isEqualTo("dispute");
        assertThat(entries.get(1).reason()).isEqualTo("상품 불일치");
    }

    @Test
    @DisplayName("배송 예외는 결제금이 보관 중인 주문만 올리고 주문이 없거나 종결됐으면 뺀다")
    void shipmentIssues_onlyForOrdersStillHoldingFunds() {
        when(shipments.awaitingPickupRegisteredBefore(NOW.minus(Duration.ofDays(3))))
                .thenReturn(List.of(
                        new QueueShipment("held", NOW.minus(Duration.ofDays(4)), null, null),
                        new QueueShipment("gone", NOW.minus(Duration.ofDays(4)), null, null),
                        new QueueShipment("closed", NOW.minus(Duration.ofDays(4)), null, null)));
        when(orders.findOrder("held")).thenReturn(Optional.of(order("held", true, NOW, null)));
        when(orders.findOrder("gone")).thenReturn(Optional.empty());
        when(orders.findOrder("closed")).thenReturn(Optional.of(order("closed", false, NOW, null)));

        List<ExceptionEntry> entries = service.list();

        assertThat(entries).extracting(ExceptionEntry::orderId).containsExactly("held");
        assertThat(entries.get(0).type()).isEqualTo("carrier_pickup_stall");
        assertThat(entries.get(0).since()).isEqualTo(NOW.minus(Duration.ofDays(4)));
    }

    @Test
    @DisplayName("배송 정체·추적 불가는 각자의 기준 시각으로 정렬된다")
    void transitAndTracker_useTheirOwnSince() {
        when(shipments.inTransitStalledSince(NOW.minus(Duration.ofDays(14))))
                .thenReturn(List.of(new QueueShipment("stalled", null, NOW.minus(Duration.ofDays(20)), null)));
        when(shipments.trackerUnknownSince(NOW.minus(Duration.ofHours(24))))
                .thenReturn(List.of(new QueueShipment("unknown", null, null, NOW.minus(Duration.ofDays(2)))));
        when(orders.findOrder("stalled")).thenReturn(Optional.of(order("stalled", true, NOW, null)));
        when(orders.findOrder("unknown")).thenReturn(Optional.of(order("unknown", true, NOW, null)));

        assertThat(service.list()).extracting(ExceptionEntry::type).containsExactly("transit_stall", "tracker_unknown");
    }

    private static QueueOrder order(String id, boolean fundsHeld, Instant statusChangedAt, Instant disputeOpenedAt) {
        return new QueueOrder(
                id,
                "disputed",
                fundsHeld,
                "buyer",
                "seller",
                10_000,
                statusChangedAt,
                disputeOpenedAt,
                "상품 불일치",
                "설명과 다름");
    }
}
