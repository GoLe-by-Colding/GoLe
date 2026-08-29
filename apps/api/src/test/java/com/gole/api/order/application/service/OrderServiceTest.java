package com.gole.api.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.common.operations.OperationalEvent;
import com.gole.api.common.operations.OperationalEventPublisher;
import com.gole.api.order.application.port.in.PlaceOrderUseCase.PlaceOrderCommand;
import com.gole.api.order.application.port.out.ListingReservationPort;
import com.gole.api.order.application.port.out.OrderIdGeneratorPort;
import com.gole.api.order.application.port.out.OrderRepositoryPort;
import com.gole.api.order.application.port.out.PaymentGatewayPort;
import com.gole.api.order.application.port.out.PaymentGatewayPort.PaymentVerification;
import com.gole.api.order.application.port.out.PaymentGatewayPort.PaymentVerificationResult;
import com.gole.api.order.application.port.out.PaymentGatewayUnavailableException;
import com.gole.api.order.application.port.out.SettlementPort;
import com.gole.api.order.domain.exception.ItemUnavailableException;
import com.gole.api.order.domain.exception.SelfPurchaseException;
import com.gole.api.order.domain.model.DisputeReason;
import com.gole.api.order.domain.model.Order;
import com.gole.api.order.domain.model.OrderStatus;
import com.gole.api.order.domain.model.PaymentMethod;
import com.gole.api.order.domain.model.PaymentMethodType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
    private RecordingPublisher events;
    private OrderService service;

    @BeforeEach
    void setUp() {
        orders = new InMemoryOrders();
        reservation = new FakeReservation();
        settlement = new CountingSettlement();
        events = new RecordingPublisher();
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        service = new OrderService(
                orders,
                reservation,
                new AlwaysApprovePayment(),
                settlement,
                (s, p, q, t, c) -> {},
                (sellerId, orderId, amount) -> {},
                new SequentialIds(),
                clock,
                new OrderPaymentTransitionService(orders, reservation),
                events);
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
    void place_rejectsSelfPurchase() {
        reservation.available = true;
        assertThatThrownBy(() -> service.place(new PlaceOrderCommand("listing-1", "seller-1")))
                .isInstanceOf(SelfPurchaseException.class);
    }

    /** 거부하면서 선점을 되돌리지 않으면 매물이 RESERVED로 굳어 아무도 사지 못한다. */
    @Test
    void place_selfPurchase_releasesReservation_andCreatesNoOrder() {
        reservation.available = true;
        assertThatThrownBy(() -> service.place(new PlaceOrderCommand("listing-1", "seller-1")))
                .isInstanceOf(SelfPurchaseException.class);
        assertThat(reservation.released).isTrue();
        assertThat(orders.findByBuyerId("seller-1")).isEmpty();
    }

    @Test
    void place_releasesReservationWhenOrderPersistenceFails() {
        reservation.available = true;
        orders.failSave = true;

        assertThatThrownBy(() -> service.place(new PlaceOrderCommand("listing-1", "buyer-1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("mongo unavailable");
        assertThat(reservation.released).isTrue();
    }

    @Test
    void place_preRegistersAmountAndReleasesReservationWhenPgIsUnavailable() {
        reservation.available = true;
        RecordingPreparePayment payment = new RecordingPreparePayment();
        service = serviceWith(payment);

        String id = service.place(new PlaceOrderCommand("listing-1", "buyer-1"));

        assertThat(payment.preparedOrderId).isEqualTo(id);
        assertThat(payment.preparedAmount).isEqualTo(280_000);

        reservation.released = false;
        payment.fail = true;
        assertThatThrownBy(() -> service.place(new PlaceOrderCommand("listing-2", "buyer-1")))
                .isInstanceOf(PaymentGatewayUnavailableException.class);
        assertThat(reservation.released).isTrue();
        assertThat(orders.store).hasSize(1);
    }

    @Test
    void fullFlow_completes_andSettlesOnce() {
        reservation.available = true;
        String id = service.place(new PlaceOrderCommand("listing-1", "buyer-1"));
        assertThat(service.pay(id)).isEqualTo(OrderStatus.FUNDS_HELD);
        assertThat(events.events).singleElement().satisfies(event -> {
            assertThat(event.category()).isEqualTo(OperationalEvent.Category.PAYMENT);
            assertThat(event.level()).isEqualTo(OperationalEvent.Level.SUCCESS);
            assertThat(event.title()).isEqualTo("결제 승인 완료");
            assertThat(event.fields())
                    .containsOnlyKeys("주문 ID", "상태")
                    .containsEntry("주문 ID", id)
                    .containsEntry("상태", OrderStatus.FUNDS_HELD.name());
        });
        service.complete(id);
        assertThat(service.getById(id).getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(settlement.calls.get()).isEqualTo(1);
        assertThat(reservation.sold).isTrue();
    }

    /**
     * A6b(R5.5) 회귀 고정: 환불된 주문은 수수료·정산을 만들지 않는다.
     * 지금은 REFUNDED 경로가 정산을 호출하지 않아 자연 충족이지만, 이 테스트가 없으면
     * 환불 흐름을 고치다 정산 호출이 끼어들어도 아무도 모른다.
     */
    @Test
    void refund_neverCreatesSettlement() {
        reservation.available = true;
        String id = service.place(new PlaceOrderCommand("listing-1", "buyer-1"));
        service.pay(id);
        service.refund(id);
        assertThat(service.getById(id).getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(settlement.calls.get()).isZero();
    }

    /** 분쟁 → 환불 판정 경로에서도 정산이 없어야 한다. (R5.5, R4.4) */
    @Test
    void disputedRefund_neverCreatesSettlement() {
        reservation.available = true;
        String id = service.place(new PlaceOrderCommand("listing-1", "buyer-1"));
        service.pay(id);
        Order order = service.getById(id);
        order.openDispute(DisputeReason.NOT_ARRIVED, "도착하지 않았어요", Instant.parse("2026-01-02T00:00:00Z"));
        orders.save(order);

        service.refund(id);
        assertThat(service.getById(id).getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(settlement.calls.get()).isZero();
        assertThat(reservation.released).isTrue();
    }

    /** 분쟁 → 완료 판정은 기존 완료 경로 그대로 — 수수료 확정 + 정산 1회. (R4.4) */
    @Test
    void disputedComplete_settlesOnce() {
        reservation.available = true;
        String id = service.place(new PlaceOrderCommand("listing-1", "buyer-1"));
        service.pay(id);
        Order order = service.getById(id);
        order.openDispute(DisputeReason.ITEM_MISMATCH, null, Instant.parse("2026-01-02T00:00:00Z"));
        orders.save(order);

        service.complete(id);
        assertThat(service.getById(id).getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(settlement.calls.get()).isEqualTo(1);
    }

    @Test
    void automaticCompletionNeverOverridesAnOpenedDispute() {
        reservation.available = true;
        String id = service.place(new PlaceOrderCommand("listing-1", "buyer-1"));
        service.pay(id);
        Order order = service.getById(id);
        order.openDispute(DisputeReason.ITEM_MISMATCH, null, Instant.parse("2026-01-02T00:00:00Z"));
        orders.save(order);

        assertThat(service.completeAutomatically(id)).isFalse();
        assertThat(service.getById(id).getStatus()).isEqualTo(OrderStatus.DISPUTED);
        assertThat(settlement.calls.get()).isZero();
        assertThat(reservation.sold).isFalse();
    }

    @Test
    void automaticCompletionFinalizesOnlyFundsHeldOrder() {
        reservation.available = true;
        String id = service.place(new PlaceOrderCommand("listing-1", "buyer-1"));
        service.pay(id);

        assertThat(service.completeAutomatically(id)).isTrue();
        assertThat(service.getById(id).getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(settlement.calls.get()).isEqualTo(1);
    }

    /** 분쟁은 FUNDS_HELD에서만 열린다 — 완료·환불 후에는 불가. (R4.1) */
    @Test
    void dispute_onlyOpensFromFundsHeld() {
        reservation.available = true;
        String id = service.place(new PlaceOrderCommand("listing-1", "buyer-1"));
        service.pay(id);
        service.complete(id);
        Order completed = service.getById(id);
        assertThatThrownBy(
                        () -> completed.openDispute(DisputeReason.DAMAGED, null, Instant.parse("2026-01-03T00:00:00Z")))
                .isInstanceOf(com.gole.api.order.domain.exception.OrderStateException.class);
    }

    @Test
    void refund_returnsFunds_andReleasesListing() {
        reservation.available = true;
        String id = service.place(new PlaceOrderCommand("listing-1", "buyer-1"));
        service.pay(id);
        events.events.clear();
        service.refund(id);
        assertThat(service.getById(id).getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(reservation.released).isTrue();
        assertThat(events.events).singleElement().satisfies(event -> {
            assertThat(event.level()).isEqualTo(OperationalEvent.Level.SUCCESS);
            assertThat(event.title()).isEqualTo("환불 완료");
            assertThat(event.fields()).containsEntry("상태", OrderStatus.REFUNDED.name());
        });

        service.refund(id);
        assertThat(events.events).hasSize(1);
    }

    @Test
    void asynchronousRefund_releasesListingOnlyAfterPgConfirmation() {
        reservation.available = true;
        String id = service.place(new PlaceOrderCommand("listing-1", "buyer-1"));
        service.pay(id);
        AsyncRefundPayment payment = new AsyncRefundPayment();
        service = serviceWith(payment);
        events.events.clear();

        service.refund(id);

        assertThat(service.getById(id).getStatus()).isEqualTo(OrderStatus.REFUND_PENDING);
        assertThat(reservation.released).isFalse();
        assertThat(events.events).singleElement().satisfies(event -> {
            assertThat(event.level()).isEqualTo(OperationalEvent.Level.WARNING);
            assertThat(event.title()).isEqualTo("환불 처리 대기");
            assertThat(event.fields()).containsEntry("상태", OrderStatus.REFUND_PENDING.name());
        });

        service.refund(id);
        assertThat(events.events).hasSize(1);

        payment.fullyRefunded = true;
        service.confirmRefund(id);

        assertThat(service.getById(id).getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(reservation.released).isTrue();
        assertThat(events.events).hasSize(2);
        assertThat(events.events.get(1).level()).isEqualTo(OperationalEvent.Level.SUCCESS);
        assertThat(events.events.get(1).title()).isEqualTo("환불 완료");

        service.confirmRefund(id);
        assertThat(events.events).hasSize(2);
    }

    @Test
    void paymentGatewayOutage_keepsOrderPending_andDoesNotReleaseListing() {
        reservation.available = true;
        String id = service.place(new PlaceOrderCommand("listing-1", "buyer-1"));
        service = new OrderService(
                orders,
                reservation,
                new UnavailablePayment(),
                settlement,
                (s, p, q, t, c) -> {},
                (sellerId, orderId, amount) -> {},
                new SequentialIds(),
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
                new OrderPaymentTransitionService(orders, reservation),
                events);

        assertThatThrownBy(() -> service.pay(id)).isInstanceOf(PaymentGatewayUnavailableException.class);
        assertThat(service.getById(id).getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(reservation.released).isFalse();
        assertThat(events.events).isEmpty();
    }

    @Test
    void pendingPayment_keepsOrderAndReservationUntilPgFinalizes() {
        reservation.available = true;
        String id = service.place(new PlaceOrderCommand("listing-1", "buyer-1"));
        service = serviceWith(new FixedVerificationPayment(PaymentVerificationResult.PENDING));

        assertThat(service.pay(id)).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(reservation.released).isFalse();
        assertThat(events.events).isEmpty();
    }

    @Test
    void missingPgPaymentDuringUserVerification_keepsOrderAndReservation() {
        reservation.available = true;
        String id = service.place(new PlaceOrderCommand("listing-1", "buyer-1"));
        service = serviceWith(new FixedVerificationPayment(PaymentVerificationResult.NOT_FOUND));

        assertThat(service.pay(id)).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(reservation.released).isFalse();
        assertThat(events.events).isEmpty();
    }

    @Test
    void finalPaymentFailure_releasesReservation() {
        reservation.available = true;
        String id = service.place(new PlaceOrderCommand("listing-1", "buyer-1"));
        service = serviceWith(new FixedVerificationPayment(PaymentVerificationResult.FAILED));

        assertThat(service.pay(id)).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(reservation.released).isTrue();
        assertThat(events.events).singleElement().satisfies(event -> {
            assertThat(event.category()).isEqualTo(OperationalEvent.Category.PAYMENT);
            assertThat(event.level()).isEqualTo(OperationalEvent.Level.ERROR);
            assertThat(event.title()).isEqualTo("결제 실패 확정");
            assertThat(event.fields())
                    .containsOnlyKeys("주문 ID", "상태")
                    .doesNotContainValue("buyer-1")
                    .doesNotContainValue("seller-1")
                    .doesNotContainValue("280000");
        });
    }

    @Test
    void suspiciousPayment_movesToReviewAndCanBeReconciledLater() {
        reservation.available = true;
        String id = service.place(new PlaceOrderCommand("listing-1", "buyer-1"));
        service = serviceWith(new FixedVerificationPayment(PaymentVerificationResult.REVIEW_REQUIRED));

        assertThat(service.pay(id)).isEqualTo(OrderStatus.PAYMENT_REVIEW);
        assertThat(reservation.released).isFalse();
        assertThat(events.events).singleElement().satisfies(event -> {
            assertThat(event.level()).isEqualTo(OperationalEvent.Level.WARNING);
            assertThat(event.title()).isEqualTo("결제 수동 확인 대기");
            assertThat(event.fields()).containsEntry("상태", OrderStatus.PAYMENT_REVIEW.name());
        });

        service = serviceWith(new FixedVerificationPayment(PaymentVerificationResult.PAID));
        assertThat(service.pay(id)).isEqualTo(OrderStatus.FUNDS_HELD);
        assertThat(events.events).hasSize(2);
        assertThat(events.events.get(1).level()).isEqualTo(OperationalEvent.Level.SUCCESS);
        assertThat(events.events.get(1).title()).isEqualTo("결제 승인 완료");
    }

    private OrderService serviceWith(PaymentGatewayPort paymentGateway) {
        return new OrderService(
                orders,
                reservation,
                paymentGateway,
                settlement,
                (s, p, q, t, c) -> {},
                (sellerId, orderId, amount) -> {},
                new SequentialIds(),
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
                new OrderPaymentTransitionService(orders, reservation),
                events);
    }

    // --- fakes ---

    private static final class InMemoryOrders implements OrderRepositoryPort {
        private final Map<String, Order> store = new HashMap<>();
        private boolean failSave;

        @Override
        public Order save(Order order) {
            if (failSave) {
                throw new IllegalStateException("mongo unavailable");
            }
            store.put(order.getId(), order);
            return order;
        }

        @Override
        public Optional<Order> findById(String orderId) {
            return Optional.ofNullable(store.get(orderId));
        }

        @Override
        public List<Order> findByBuyerId(String buyerId) {
            return store.values().stream()
                    .filter(o -> o.getBuyerId().equals(buyerId))
                    .toList();
        }

        @Override
        public List<Order> findBySellerId(String sellerId) {
            return store.values().stream()
                    .filter(o -> o.getSellerId().equals(sellerId))
                    .toList();
        }
    }

    private static final class FakeReservation implements ListingReservationPort {
        private boolean available = true;
        private boolean sold = false;
        private boolean released = false;

        @Override
        public Optional<ReservedListing> reserve(String listingId) {
            return available
                    ? Optional.of(new ReservedListing(listingId, "seller-1", "10307", 280_000, "new_sealed"))
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

    private static class AlwaysApprovePayment implements PaymentGatewayPort {
        @Override
        public PaymentVerification verifyPayment(String orderId, long amount) {
            return PaymentVerification.paid(new PaymentMethod(PaymentMethodType.EASY_PAY, "KAKAOPAY"));
        }

        @Override
        public RefundResult refund(String orderId, long amount) {
            return RefundResult.SUCCEEDED;
        }

        @Override
        public boolean isFullyRefunded(String orderId, long amount) {
            return true;
        }
    }

    private static final class RecordingPreparePayment extends AlwaysApprovePayment {
        private String preparedOrderId;
        private long preparedAmount;
        private boolean fail;

        @Override
        public void preparePayment(String orderId, long amount) {
            if (fail) {
                throw new PaymentGatewayUnavailableException(
                        orderId, new IllegalStateException("pre-register unavailable"));
            }
            preparedOrderId = orderId;
            preparedAmount = amount;
        }
    }

    private record FixedVerificationPayment(PaymentVerificationResult result) implements PaymentGatewayPort {
        @Override
        public PaymentVerification verifyPayment(String orderId, long amount) {
            return PaymentVerification.of(result);
        }

        @Override
        public RefundResult refund(String orderId, long amount) {
            return RefundResult.SUCCEEDED;
        }

        @Override
        public boolean isFullyRefunded(String orderId, long amount) {
            return true;
        }
    }

    private static final class UnavailablePayment implements PaymentGatewayPort {
        @Override
        public PaymentVerification verifyPayment(String orderId, long amount) {
            throw new PaymentGatewayUnavailableException(orderId, new IllegalStateException("portone timeout"));
        }

        @Override
        public RefundResult refund(String orderId, long amount) {
            throw new PaymentGatewayUnavailableException(orderId, new IllegalStateException("portone timeout"));
        }

        @Override
        public boolean isFullyRefunded(String orderId, long amount) {
            throw new PaymentGatewayUnavailableException(orderId, new IllegalStateException("portone timeout"));
        }
    }

    private static final class AsyncRefundPayment implements PaymentGatewayPort {
        private boolean fullyRefunded;

        @Override
        public PaymentVerification verifyPayment(String orderId, long amount) {
            return PaymentVerification.paid(new PaymentMethod(PaymentMethodType.EASY_PAY, "KAKAOPAY"));
        }

        @Override
        public RefundResult refund(String orderId, long amount) {
            return RefundResult.REQUESTED;
        }

        @Override
        public boolean isFullyRefunded(String orderId, long amount) {
            return fullyRefunded;
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

    private static final class RecordingPublisher implements OperationalEventPublisher {
        private final List<OperationalEvent> events = new ArrayList<>();

        @Override
        public void publish(OperationalEvent event) {
            events.add(event);
        }
    }
}
