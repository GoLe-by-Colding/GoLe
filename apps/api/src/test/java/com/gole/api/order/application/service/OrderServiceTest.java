package com.gole.api.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.order.application.port.in.PlaceOrderUseCase.PlaceOrderCommand;
import com.gole.api.order.application.port.out.ExecutedPriceRecorderPort;
import com.gole.api.order.application.port.out.ListingReservationPort;
import com.gole.api.order.application.port.out.OrderIdGeneratorPort;
import com.gole.api.order.application.port.out.OrderRepositoryPort;
import com.gole.api.order.application.port.out.PaymentGatewayPort;
import com.gole.api.order.application.port.out.SettlementPort;
import com.gole.api.order.domain.exception.ItemUnavailableException;
import com.gole.api.order.domain.model.Order;
import com.gole.api.order.domain.model.OrderStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderServiceTest {

    private InMemoryOrders orders;
    private FakeReservation reservation;
    private CountingSettlement settlement;
    private OrderService service;

    @BeforeEach
    void setUp() {
        orders = new InMemoryOrders();
        reservation = new FakeReservation();
        settlement = new CountingSettlement();
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        service = new OrderService(
                orders, reservation, new AlwaysApprovePayment(), settlement,
                (s, p, q, t) -> { }, (sellerId, orderId, amount) -> { },
                new SequentialIds(), clock);
    }

    @Test
    void place_reservesListing_andCreatesPendingOrder() {
        reservation.available = true;
        String id = service.place(new PlaceOrderCommand("listing-1", "buyer-1"));
        Order order = service.getById(id);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(order.getAmount()).isEqualTo(280_000);
    }

    @Test
    void place_rejectsWhenUnavailable() {
        reservation.available = false;
        assertThatThrownBy(() -> service.place(new PlaceOrderCommand("listing-1", "buyer-1")))
                .isInstanceOf(ItemUnavailableException.class);
    }

    @Test
    void fullFlow_completes_andSettlesOnce() {
        reservation.available = true;
        String id = service.place(new PlaceOrderCommand("listing-1", "buyer-1"));
        assertThat(service.pay(id)).isEqualTo(OrderStatus.FUNDS_HELD);
        service.complete(id);
        assertThat(service.getById(id).getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(settlement.calls.get()).isEqualTo(1);
        assertThat(reservation.sold).isTrue();
    }

    @Test
    void refund_returnsFunds_andReleasesListing() {
        reservation.available = true;
        String id = service.place(new PlaceOrderCommand("listing-1", "buyer-1"));
        service.pay(id);
        service.refund(id);
        assertThat(service.getById(id).getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(reservation.released).isTrue();
    }

    // --- fakes ---

    private static final class InMemoryOrders implements OrderRepositoryPort {
        private final Map<String, Order> store = new HashMap<>();

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
            return store.values().stream().filter(o -> o.getBuyerId().equals(buyerId)).toList();
        }

        @Override
        public List<Order> findBySellerId(String sellerId) {
            return store.values().stream().filter(o -> o.getSellerId().equals(sellerId)).toList();
        }
    }

    private static final class FakeReservation implements ListingReservationPort {
        private boolean available = true;
        private boolean sold = false;
        private boolean released = false;

        @Override
        public Optional<ReservedListing> reserve(String listingId) {
            return available
                    ? Optional.of(new ReservedListing(listingId, "seller-1", "10307", 280_000))
                    : Optional.empty();
        }

        @Override
        public void markSold(String listingId) {
            sold = true;
        }

        @Override
        public void release(String listingId) {
            released = true;
        }
    }

    private static final class AlwaysApprovePayment implements PaymentGatewayPort {
        @Override
        public boolean authorize(String orderId, long amount) {
            return true;
        }

        @Override
        public void refund(String orderId, long amount) {
        }
    }

    private static final class CountingSettlement implements SettlementPort {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public void settleOnce(String orderId, String sellerId, long grossAmount) {
            calls.incrementAndGet();
        }
    }

    private static final class SequentialIds implements OrderIdGeneratorPort {
        private int n = 0;

        @Override
        public String newOrderId() {
            return "order-" + (++n);
        }
    }
}
