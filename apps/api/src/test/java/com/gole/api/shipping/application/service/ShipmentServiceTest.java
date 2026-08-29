package com.gole.api.shipping.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.order.application.port.in.PrepareShipmentRegistrationUseCase;
import com.gole.api.order.domain.exception.OrderNotFoundException;
import com.gole.api.order.domain.model.Order;
import com.gole.api.order.domain.model.OrderStatus;
import com.gole.api.shipping.application.port.in.RegisterWaybillUseCase.RegisterWaybillCommand;
import com.gole.api.shipping.application.port.out.DeliveryTrackerPort;
import com.gole.api.shipping.application.port.out.ShipmentNotifierPort;
import com.gole.api.shipping.application.port.out.ShipmentRepositoryPort;
import com.gole.api.shipping.application.port.out.TrackerCachePort;
import com.gole.api.shipping.domain.exception.ShipmentStateException;
import com.gole.api.shipping.domain.model.Carrier;
import com.gole.api.shipping.domain.model.DeliveryStatus;
import com.gole.api.shipping.domain.model.Shipment;
import com.gole.api.shipping.domain.model.WaybillNumber;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ShipmentServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");

    private InMemoryShipments shipments;
    private FakeTracker tracker;
    private RecordingNotifier notifier;
    private FakeOrders orders;
    private ShipmentService service;

    @BeforeEach
    void setUp() {
        shipments = new InMemoryShipments();
        tracker = new FakeTracker();
        notifier = new RecordingNotifier();
        orders = new FakeOrders();
        service = new ShipmentService(
                shipments,
                tracker,
                new NoopCache(),
                notifier,
                orders,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(10),
                Duration.ofHours(24));
    }

    @Test
    void register_createsShipmentAndNotifiesBuyer() {
        orders.put(order("order-1", OrderStatus.FUNDS_HELD));
        Shipment saved = service.register(
                new RegisterWaybillCommand("order-1", "seller-1", "cj_logistics", "1234-5678-9012", "010-1234-5678"));

        assertThat(saved.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(saved.getWaybill().value()).isEqualTo("123456789012");
        assertThat(saved.getSellerPhone()).isEqualTo("01012345678");
        assertThat(orders.store.get("order-1").getShipmentRegisteredAt()).isEqualTo(NOW);
        assertThat(notifier.waybillRegistered).containsExactly("buyer-1");
    }

    @Test
    void register_rejectsNonSeller() {
        orders.put(order("order-1", OrderStatus.FUNDS_HELD));
        assertThatThrownBy(() -> service.register(
                        new RegisterWaybillCommand("order-1", "intruder", "cj_logistics", "123456789012", null)))
                .isInstanceOf(ForbiddenException.class);
        assertThat(shipments.store).isEmpty();
    }

    @Test
    void register_requiresFundsHeld() {
        orders.put(order("order-1", OrderStatus.PAYMENT_PENDING));
        assertThatThrownBy(() -> service.register(
                        new RegisterWaybillCommand("order-1", "seller-1", "cj_logistics", "123456789012", null)))
                .isInstanceOf(ShipmentStateException.class);
    }

    @Test
    void register_replacesExistingWaybillKeepingHistory() {
        orders.put(order("order-1", OrderStatus.FUNDS_HELD));
        service.register(
                new RegisterWaybillCommand("order-1", "seller-1", "cj_logistics", "123456789012", "01012345678"));
        Shipment replaced =
                service.register(new RegisterWaybillCommand("order-1", "seller-1", "hanjin", "998877665544", null));

        assertThat(replaced.getCarrier()).isEqualTo(Carrier.HANJIN);
        assertThat(replaced.getHistory()).hasSize(1);
        assertThat(shipments.store).hasSize(1);
    }

    @Test
    void track_appliesTrackerResult_andNotifiesOnDelivery() {
        orders.put(order("order-1", OrderStatus.FUNDS_HELD));
        service.register(new RegisterWaybillCommand("order-1", "seller-1", "cj_logistics", "123456789012", null));

        tracker.next = new DeliveryTrackerPort.TrackingResult(DeliveryStatus.IN_TRANSIT, "간선상차");
        assertThat(service.track("order-1").getStatus()).isEqualTo(DeliveryStatus.IN_TRANSIT);
        assertThat(notifier.delivered).isEmpty();

        tracker.next = new DeliveryTrackerPort.TrackingResult(DeliveryStatus.DELIVERED, "배달완료");
        Shipment delivered = service.track("order-1");
        assertThat(delivered.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(delivered.getDeliveredAt()).isEqualTo(NOW);
        assertThat(notifier.delivered).containsExactly("order-1");

        // 종결 후에는 트래커를 다시 부르지 않고, 재알림도 없다 (멱등)
        tracker.next = null;
        service.track("order-1");
        assertThat(notifier.delivered).hasSize(1);
    }

    @Test
    void track_treatsUnknownAsNonFatal() {
        orders.put(order("order-1", OrderStatus.FUNDS_HELD));
        service.register(new RegisterWaybillCommand("order-1", "seller-1", "cj_logistics", "123456789012", null));

        tracker.next = new DeliveryTrackerPort.TrackingResult(DeliveryStatus.UNKNOWN, null);
        Shipment tracked = service.track("order-1");
        assertThat(tracked.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(tracked.getUnknownSince()).isEqualTo(NOW);
    }

    private static Order order(String id, OrderStatus status) {
        return new Order(
                id, "listing-1", "buyer-1", "seller-1", null, null, 100_000, status, NOW, new ArrayList<>(), null);
    }

    // ---- fakes ----

    static class InMemoryShipments implements ShipmentRepositoryPort {
        final Map<String, Shipment> store = new HashMap<>();

        @Override
        public Shipment save(Shipment shipment) {
            store.put(shipment.getOrderId(), shipment);
            return shipment;
        }

        @Override
        public Optional<Shipment> findByOrderId(String orderId) {
            return Optional.ofNullable(store.get(orderId));
        }

        @Override
        public List<Shipment> findTrackable(int limit) {
            return store.values().stream()
                    .filter(s -> s.getStatus() != DeliveryStatus.DELIVERED)
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<Shipment> findByStatusAndDeliveredAtBefore(DeliveryStatus status, Instant cutoff) {
            return store.values().stream()
                    .filter(s -> s.getStatus() == status
                            && s.getDeliveredAt() != null
                            && s.getDeliveredAt().isBefore(cutoff))
                    .toList();
        }

        @Override
        public List<Shipment> findByStatusAndRegisteredAtBefore(DeliveryStatus status, Instant cutoff) {
            return store.values().stream()
                    .filter(s -> s.getStatus() == status && s.getRegisteredAt().isBefore(cutoff))
                    .toList();
        }

        @Override
        public List<Shipment> findByStatusAndStatusChangedAtBefore(DeliveryStatus status, Instant cutoff) {
            return store.values().stream()
                    .filter(s ->
                            s.getStatus() == status && s.getStatusChangedAt().isBefore(cutoff))
                    .toList();
        }

        @Override
        public List<Shipment> findByUnknownSinceBefore(Instant cutoff) {
            return store.values().stream()
                    .filter(s ->
                            s.getUnknownSince() != null && s.getUnknownSince().isBefore(cutoff))
                    .toList();
        }
    }

    static class FakeTracker implements DeliveryTrackerPort {
        TrackingResult next;

        @Override
        public boolean isConfigured() {
            return false;
        }

        @Override
        public TrackingResult track(TrackingQuery query) {
            if (next == null) {
                throw new AssertionError("tracker should not be called");
            }
            return next;
        }
    }

    static class NoopCache implements TrackerCachePort {
        @Override
        public Optional<DeliveryTrackerPort.TrackingResult> get(Carrier carrier, WaybillNumber waybill) {
            return Optional.empty();
        }

        @Override
        public void put(
                Carrier carrier, WaybillNumber waybill, DeliveryTrackerPort.TrackingResult result, Duration ttl) {}
    }

    static class RecordingNotifier implements ShipmentNotifierPort {
        final List<String> waybillRegistered = new ArrayList<>();
        final List<String> delivered = new ArrayList<>();

        @Override
        public void notifyWaybillRegistered(String buyerId, String orderId, String carrierLabel, String waybill) {
            waybillRegistered.add(buyerId);
        }

        @Override
        public void notifyDelivered(String buyerId, String sellerId, String orderId) {
            delivered.add(orderId);
        }
    }

    static class FakeOrders implements PrepareShipmentRegistrationUseCase {
        private final Map<String, Order> store = new HashMap<>();

        void put(Order order) {
            store.put(order.getId(), order);
        }

        @Override
        public Order prepare(String orderId, String sellerId) {
            Order order = store.get(orderId);
            if (order == null) {
                throw new OrderNotFoundException(orderId);
            }
            if (!order.getSellerId().equals(sellerId)) {
                throw new ForbiddenException("SHIPMENT_ACCESS_DENIED", "주문의 판매자만 운송장을 등록할 수 있습니다");
            }
            order.registerShipment(NOW);
            return order;
        }
    }
}
