package com.gole.api.order.application.service.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.order.application.port.in.CompleteOrderUseCase;
import com.gole.api.order.application.port.in.RefundOrderUseCase;
import com.gole.api.order.application.port.out.OrderEventNotifierPort;
import com.gole.api.order.application.port.out.OrderRepositoryPort;
import com.gole.api.order.application.port.out.PipelineMarkerPort;
import com.gole.api.order.domain.model.Order;
import com.gole.api.order.domain.model.OrderStatus;
import com.gole.api.shipping.application.port.in.GetShipmentUseCase;
import com.gole.api.shipping.domain.model.Carrier;
import com.gole.api.shipping.domain.model.DeliveryStatus;
import com.gole.api.shipping.domain.model.Shipment;
import com.gole.api.shipping.domain.model.WaybillNumber;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 파이프라인 규칙 경계값 테스트 (R9 표). 고정 시각으로 타임아웃 전후를 검증한다.
 */
class PipelineRulesTest {

    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");

    private InMemoryOrders orders;
    private FakeShipments shipments;
    private RecordingNotifier notifier;
    private InMemoryMarkers markers;
    private PipelineProperties properties;

    @BeforeEach
    void setUp() {
        orders = new InMemoryOrders();
        shipments = new FakeShipments();
        notifier = new RecordingNotifier();
        markers = new InMemoryMarkers();
        properties = new PipelineProperties(); // 기본값(R9 표) 사용
    }

    private Order fundsHeldOrder(String id, Instant changedAt) {
        List<com.gole.api.order.domain.model.OrderStatusChange> history = new ArrayList<>();
        history.add(new com.gole.api.order.domain.model.OrderStatusChange(OrderStatus.PAYMENT_PENDING, changedAt));
        history.add(new com.gole.api.order.domain.model.OrderStatusChange(OrderStatus.FUNDS_HELD, changedAt));
        Order order = new Order(
                id,
                "listing-" + id,
                "buyer-1",
                "seller-1",
                null,
                null,
                100_000,
                OrderStatus.FUNDS_HELD,
                changedAt,
                history,
                null);
        orders.put(order);
        return order;
    }

    @Test
    void unshippedReminder_firesOnceAfterThreeDays() {
        fundsHeldOrder("o-old", NOW.minus(Duration.ofDays(3).plusMinutes(1)));
        fundsHeldOrder("o-fresh", NOW.minus(Duration.ofDays(2)));

        UnshippedReminderRule rule = new UnshippedReminderRule(orders, shipments, notifier, markers, properties);
        assertThat(rule.candidates(NOW)).containsExactly("o-old");
        assertThat(rule.apply("o-old", NOW)).isTrue();
        assertThat(notifier.reminders).containsExactly("o-old");

        // 두 번째 주기 — 마커 덕에 중복 알림이 없다 (R7.3)
        assertThat(rule.apply("o-old", NOW)).isFalse();
        assertThat(notifier.reminders).hasSize(1);
    }

    @Test
    void unshippedReminder_skipsShippedOrders() {
        fundsHeldOrder("o-shipped", NOW.minus(Duration.ofDays(4)));
        shipments.put(shipment("o-shipped", DeliveryStatus.PENDING, NOW.minus(Duration.ofDays(1)), null));

        UnshippedReminderRule rule = new UnshippedReminderRule(orders, shipments, notifier, markers, properties);
        assertThat(rule.apply("o-shipped", NOW)).isFalse();
        assertThat(notifier.reminders).isEmpty();
    }

    @Test
    void unshippedAutoRefund_refundsAfterSevenDays_butNotWhenShipped() {
        fundsHeldOrder("o-refund", NOW.minus(Duration.ofDays(7).plusMinutes(1)));
        fundsHeldOrder("o-shipped", NOW.minus(Duration.ofDays(8)));
        shipments.put(shipment("o-shipped", DeliveryStatus.IN_TRANSIT, NOW.minus(Duration.ofDays(8)), null));

        RecordingRefund refund = new RecordingRefund(orders);
        UnshippedAutoRefundRule rule = new UnshippedAutoRefundRule(orders, shipments, refund, notifier, properties);

        assertThat(rule.candidates(NOW)).containsExactlyInAnyOrder("o-refund", "o-shipped");
        assertThat(rule.apply("o-refund", NOW)).isTrue();
        assertThat(rule.apply("o-shipped", NOW)).isFalse(); // 발송됨 — 환불 금지
        assertThat(refund.refunded).containsExactly("o-refund");

        // 멱등 — 이미 환불된 주문은 상태 검사에서 걸러진다 (R7.3)
        assertThat(rule.apply("o-refund", NOW)).isFalse();
        assertThat(refund.refunded).hasSize(1);
    }

    @Test
    void autoComplete_completesSevenDaysAfterDelivery_andStopsOnDispute() {
        Order eligible = fundsHeldOrder("o-done", NOW.minus(Duration.ofDays(10)));
        shipments.put(shipment(
                "o-done",
                DeliveryStatus.DELIVERED,
                NOW.minus(Duration.ofDays(10)),
                NOW.minus(Duration.ofDays(7).plusMinutes(1))));

        Order disputed = fundsHeldOrder("o-disputed", NOW.minus(Duration.ofDays(10)));
        disputed.openDispute(
                com.gole.api.order.domain.model.DisputeReason.NOT_ARRIVED, null, NOW.minus(Duration.ofDays(1)));
        shipments.put(shipment(
                "o-disputed", DeliveryStatus.DELIVERED, NOW.minus(Duration.ofDays(10)), NOW.minus(Duration.ofDays(8))));

        RecordingComplete complete = new RecordingComplete(orders);
        AutoCompleteDeliveredRule rule =
                new AutoCompleteDeliveredRule(shipments, orders, complete, notifier, properties);

        assertThat(rule.candidates(NOW)).containsExactlyInAnyOrder("o-done", "o-disputed");
        assertThat(rule.apply("o-done", NOW)).isTrue();
        // 분쟁 중 — 타이머 정지 (R4.2)
        assertThat(rule.apply("o-disputed", NOW)).isFalse();
        assertThat(complete.completed).containsExactly("o-done");
        assertThat(eligible.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(disputed.getStatus()).isEqualTo(OrderStatus.DISPUTED);
    }

    @Test
    void autoComplete_boundaryJustBeforeSevenDaysIsExcluded() {
        fundsHeldOrder("o-early", NOW.minus(Duration.ofDays(10)));
        shipments.put(shipment(
                "o-early",
                DeliveryStatus.DELIVERED,
                NOW.minus(Duration.ofDays(10)),
                NOW.minus(Duration.ofDays(7).minusMinutes(1))));

        AutoCompleteDeliveredRule rule =
                new AutoCompleteDeliveredRule(shipments, orders, new RecordingComplete(orders), notifier, properties);
        assertThat(rule.candidates(NOW)).isEmpty();
    }

    private static Shipment shipment(String orderId, DeliveryStatus status, Instant registeredAt, Instant deliveredAt) {
        return new Shipment(
                "s-" + orderId,
                orderId,
                "seller-1",
                "buyer-1",
                null,
                Carrier.CJ_LOGISTICS,
                new WaybillNumber("123456789012"),
                status,
                null,
                registeredAt,
                registeredAt,
                deliveredAt,
                null,
                null,
                new ArrayList<>(),
                null);
    }

    // ---- fakes ----

    static class InMemoryOrders implements OrderRepositoryPort {
        final Map<String, Order> store = new HashMap<>();

        void put(Order order) {
            store.put(order.getId(), order);
        }

        @Override
        public Order save(Order order) {
            store.put(order.getId(), order);
            return order;
        }

        @Override
        public Optional<Order> findById(String orderId) {
            return Optional.ofNullable(store.get(orderId));
        }

        @Override
        public List<Order> findByBuyerId(String buyerId) {
            return List.of();
        }

        @Override
        public List<Order> findBySellerId(String sellerId) {
            return List.of();
        }

        @Override
        public List<Order> findByStatusChangedBefore(OrderStatus status, Instant cutoff) {
            return store.values().stream()
                    .filter(o ->
                            o.getStatus() == status && o.getStatusChangedAt().isBefore(cutoff))
                    .toList();
        }
    }

    static class FakeShipments implements GetShipmentUseCase {
        final Map<String, Shipment> store = new HashMap<>();

        void put(Shipment shipment) {
            store.put(shipment.getOrderId(), shipment);
        }

        @Override
        public Optional<Shipment> getByOrderId(String orderId) {
            return Optional.ofNullable(store.get(orderId));
        }

        @Override
        public List<Shipment> findDeliveredBefore(Instant cutoff) {
            return store.values().stream()
                    .filter(s -> s.getStatus() == DeliveryStatus.DELIVERED
                            && s.getDeliveredAt() != null
                            && s.getDeliveredAt().isBefore(cutoff))
                    .toList();
        }

        @Override
        public List<Shipment> findPendingRegisteredBefore(Instant cutoff) {
            return List.of();
        }

        @Override
        public List<Shipment> findInTransitStalledSince(Instant cutoff) {
            return List.of();
        }

        @Override
        public List<Shipment> findUnknownSince(Instant cutoff) {
            return List.of();
        }
    }

    static class InMemoryMarkers implements PipelineMarkerPort {
        final Set<String> marked = new HashSet<>();

        @Override
        public boolean markOnce(String rule, String refId) {
            return marked.add(rule + ":" + refId);
        }
    }

    static class RecordingNotifier implements OrderEventNotifierPort {
        final List<String> reminders = new ArrayList<>();
        final List<String> autoRefunds = new ArrayList<>();
        final List<String> autoCompletes = new ArrayList<>();

        @Override
        public void disputeOpened(String sellerId, String orderId, String reasonLabel) {}

        @Override
        public void disputeResolved(String buyerId, String sellerId, String orderId, boolean refunded) {}

        @Override
        public void autoRefundedForNoShipment(String buyerId, String sellerId, String orderId) {
            autoRefunds.add(orderId);
        }

        @Override
        public void shipmentReminder(String sellerId, String orderId) {
            reminders.add(orderId);
        }

        @Override
        public void autoCompleted(String buyerId, String sellerId, String orderId) {
            autoCompletes.add(orderId);
        }
    }

    static class RecordingRefund implements RefundOrderUseCase {
        final List<String> refunded = new ArrayList<>();
        private final InMemoryOrders orders;

        RecordingRefund(InMemoryOrders orders) {
            this.orders = orders;
        }

        @Override
        public void refund(String orderId) {
            refunded.add(orderId);
            orders.store.get(orderId).refund(NOW);
        }
    }

    static class RecordingComplete implements CompleteOrderUseCase {
        final List<String> completed = new ArrayList<>();
        private final InMemoryOrders orders;

        RecordingComplete(InMemoryOrders orders) {
            this.orders = orders;
        }

        @Override
        public void complete(String orderId) {
            completed.add(orderId);
            orders.store.get(orderId).complete(NOW);
        }
    }
}
