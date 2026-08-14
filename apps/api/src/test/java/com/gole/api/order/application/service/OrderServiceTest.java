package com.gole.api.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.order.application.port.in.PlaceOrderUseCase.PlaceOrderCommand;
import com.gole.api.order.application.port.out.ListingReservationPort;
import com.gole.api.order.application.port.out.OrderIdGeneratorPort;
import com.gole.api.order.application.port.out.OrderRepositoryPort;
import com.gole.api.order.application.port.out.PaymentGatewayPort;
import com.gole.api.order.application.port.out.PaymentGatewayPort.PaymentAuthorization;
import com.gole.api.order.application.port.out.SettlementPort;
import com.gole.api.order.domain.exception.ItemUnavailableException;
import com.gole.api.order.domain.exception.OrderStateException;
import com.gole.api.order.domain.exception.SelfPurchaseException;
import com.gole.api.order.domain.model.FeePolicy;
import com.gole.api.order.domain.model.Order;
import com.gole.api.order.domain.model.OrderStatus;
import com.gole.api.order.domain.model.PaymentMethod;
import com.gole.api.order.domain.model.PaymentMethodType;
import com.gole.api.order.domain.model.Settlement;
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
    private FakePayment payment;
    private OrderService service;

    @BeforeEach
    void setUp() {
        orders = new InMemoryOrders();
        reservation = new FakeReservation();
        settlement = new CountingSettlement();
        payment = new FakePayment();
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        service = new OrderService(
                orders,
                reservation,
                payment,
                settlement,
                (s, p, q, t, c) -> {},
                (sellerId, orderId, amount) -> {},
                new SequentialIds(),
                clock);
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
    void fullFlow_completes_andSettlesOnce() {
        reservation.available = true;
        String id = service.place(new PlaceOrderCommand("listing-1", "buyer-1"));
        assertThat(service.pay(id)).isEqualTo(OrderStatus.FUNDS_HELD);
        service.complete(id);
        assertThat(service.getById(id).getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(settlement.calls.get()).isEqualTo(1);
        assertThat(reservation.sold).isTrue();
    }

    /**
     * 회귀: 정산 전표가 주문과 함께 저장되어야 한다.
     *
     * <p>이전에는 정산 어댑터가 계산만 하고 아무 데도 남기지 않았고, 영속성 어댑터도 정산을
     * null로 고정 저장했다. 그래서 완료 주문에서 수수료·정산액을 되읽을 수 없었다.
     */
    @Test
    void complete_attachesSettlement_toSavedOrder() {
        reservation.available = true;
        String id = service.place(new PlaceOrderCommand("listing-1", "buyer-1"));
        service.pay(id);
        service.complete(id);

        Settlement saved = orders.findById(id).orElseThrow().getSettlement();
        assertThat(saved).isNotNull();
        assertThat(saved.grossAmount()).isEqualTo(280_000);
        assertThat(saved.fee()).isEqualTo(14_000); // 5%
        assertThat(saved.payout()).isEqualTo(266_000);
        assertThat(saved.feeRate()).isEqualTo(0.05);
        // 자금 보존: 판매자 지급액 + 플랫폼 수수료 = 구매자가 낸 금액
        assertThat(saved.payout() + saved.fee()).isEqualTo(saved.grossAmount());
    }

    /** 완료 전에는 정산이 없어야 한다 — 0원 정산과 미정산이 구분되어야 하기 때문. */
    @Test
    void settlement_isAbsent_beforeCompletion() {
        reservation.available = true;
        String id = service.place(new PlaceOrderCommand("listing-1", "buyer-1"));
        assertThat(orders.findById(id).orElseThrow().getSettlement()).isNull();
        service.pay(id);
        assertThat(orders.findById(id).orElseThrow().getSettlement()).isNull();
    }

    /**
     * 이중 정산 방어. 상태 전이가 이미 재실행을 막지만, 나중에 재정산·수동 보정 경로가
     * 생겼을 때 조용히 두 번 계산되지 않도록 애그리거트에서도 거부한다.
     */
    @Test
    void attachSettlement_rejectsSecondAttempt() {
        reservation.available = true;
        String id = service.place(new PlaceOrderCommand("listing-1", "buyer-1"));
        service.pay(id);
        service.complete(id);

        Order completed = orders.findById(id).orElseThrow();
        assertThatThrownBy(() -> completed.attachSettlement(
                        Settlement.compute(id, "seller-1", 280_000, new FeePolicy(0.05, 0, 0), Instant.EPOCH)))
                .isInstanceOf(OrderStateException.class);
    }

    /**
     * 결제수단은 승인 시점에 주문에 새겨져야 한다.
     *
     * <p>PG가 알려준 결제수단을 버리면, 나중에 "이 주문 카카오페이로 결제한 건데요"라는 문의에
     * 우리 데이터로는 답할 수 없다. 프론트가 보내는 값은 요청 의도일 뿐 승인 결과가 아니다.
     */
    @Test
    void pay_recordsPaymentMethod_fromGateway() {
        reservation.available = true;
        String id = service.place(new PlaceOrderCommand("listing-1", "buyer-1"));

        service.pay(id);

        PaymentMethod method = orders.findById(id).orElseThrow().getPaymentMethod();
        assertThat(method).isNotNull();
        assertThat(method.type()).isEqualTo(PaymentMethodType.EASY_PAY);
        assertThat(method.provider()).isEqualTo("KAKAOPAY");
    }

    @Test
    void paymentMethod_isAbsent_beforePayment() {
        reservation.available = true;
        String id = service.place(new PlaceOrderCommand("listing-1", "buyer-1"));

        assertThat(orders.findById(id).orElseThrow().getPaymentMethod()).isNull();
    }

    /** 승인 실패 주문에 결제수단이 남으면 "결제된 것처럼" 보인다. */
    @Test
    void payDeclined_recordsNoPaymentMethod() {
        reservation.available = true;
        payment.approve = false;
        String id = service.place(new PlaceOrderCommand("listing-1", "buyer-1"));

        assertThat(service.pay(id)).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(orders.findById(id).orElseThrow().getPaymentMethod()).isNull();
    }

    /** 결제수단을 모르는 PG(스텁 포함)라도 승인 자체는 진행되어야 한다. */
    @Test
    void pay_succeedsEvenWhenGatewayOmitsMethod() {
        reservation.available = true;
        payment.method = null;
        String id = service.place(new PlaceOrderCommand("listing-1", "buyer-1"));

        assertThat(service.pay(id)).isEqualTo(OrderStatus.FUNDS_HELD);
        assertThat(orders.findById(id).orElseThrow().getPaymentMethod()).isNull();
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

    private static final class FakePayment implements PaymentGatewayPort {
        private boolean approve = true;
        private PaymentMethod method = new PaymentMethod(PaymentMethodType.EASY_PAY, "KAKAOPAY");

        @Override
        public PaymentAuthorization authorize(String orderId, long amount) {
            return approve ? PaymentAuthorization.approved(method) : PaymentAuthorization.declined();
        }

        @Override
        public void refund(String orderId, long amount) {}
    }

    private static final class CountingSettlement implements SettlementPort {
        private static final FeePolicy POLICY = new FeePolicy(0.05, 0, 0);

        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public Settlement settleOnce(String orderId, String sellerId, long grossAmount) {
            calls.incrementAndGet();
            return Settlement.compute(orderId, sellerId, grossAmount, POLICY, Instant.EPOCH);
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
