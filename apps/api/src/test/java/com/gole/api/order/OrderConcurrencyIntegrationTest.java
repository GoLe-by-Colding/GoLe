package com.gole.api.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.gole.api.common.exception.ConflictException;
import com.gole.api.listing.application.port.in.CreateListingUseCase;
import com.gole.api.listing.application.port.in.CreateListingUseCase.CreateListingCommand;
import com.gole.api.listing.application.port.in.GetListingUseCase;
import com.gole.api.listing.domain.model.ItemCondition;
import com.gole.api.listing.domain.model.ListingStatus;
import com.gole.api.order.application.port.in.CompleteOrderUseCase;
import com.gole.api.order.application.port.in.GetOrderUseCase;
import com.gole.api.order.application.port.in.ManageSettlementsUseCase;
import com.gole.api.order.application.port.in.OpenDisputeUseCase;
import com.gole.api.order.application.port.in.OpenDisputeUseCase.OpenDisputeCommand;
import com.gole.api.order.application.port.in.PayOrderUseCase;
import com.gole.api.order.application.port.in.PlaceOrderUseCase;
import com.gole.api.order.application.port.in.PlaceOrderUseCase.PlaceOrderCommand;
import com.gole.api.order.application.port.in.RefundOrderUseCase;
import com.gole.api.order.domain.exception.ItemUnavailableException;
import com.gole.api.order.domain.exception.OrderStateException;
import com.gole.api.order.domain.model.OrderStatus;
import com.gole.api.pricing.application.port.out.PriceTransactionRepositoryPort;
import com.gole.api.shipping.application.port.in.GetShipmentUseCase;
import com.gole.api.shipping.application.port.in.RegisterWaybillUseCase;
import com.gole.api.shipping.application.port.in.RegisterWaybillUseCase.RegisterWaybillCommand;
import com.gole.api.shipping.application.port.out.ShipmentNotifierPort;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 정합성 통합 테스트(Testcontainers MongoDB replica set).
 * 설계 Property 1(단일 낙찰), Property 2(exactly-once 정산)를 검증한다.
 */
@SpringBootTest
@Testcontainers
class OrderConcurrencyIntegrationTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        // 시더 비활성(테스트 격리)
        registry.add("gole.catalog.seed-on-empty", () -> "false");
        registry.add("gole.listing.seed-on-empty", () -> "false");
        registry.add("gole.pricing.seed-on-empty", () -> "false");
        registry.add("gole.community.seed-on-empty", () -> "false");
        // 이 테스트는 유예 정책이 아니라 지급 증빙의 전역 유일성을 검증한다.
        registry.add("gole.settlement.mode", () -> "MANUAL");
        registry.add("gole.settlement.payout-contract-verified", () -> "true");
        registry.add("gole.settlement.payout-holdback", () -> "0s");
    }

    @Autowired
    CreateListingUseCase createListing;

    @Autowired
    GetListingUseCase getListing;

    @Autowired
    PlaceOrderUseCase placeOrder;

    @Autowired
    PayOrderUseCase payOrder;

    @Autowired
    CompleteOrderUseCase completeOrder;

    @Autowired
    RefundOrderUseCase refundOrder;

    @Autowired
    GetOrderUseCase getOrder;

    @Autowired
    OpenDisputeUseCase openDispute;

    @Autowired
    RegisterWaybillUseCase registerWaybill;

    @Autowired
    GetShipmentUseCase getShipment;

    @Autowired
    ManageSettlementsUseCase settlements;

    @Autowired
    PriceTransactionRepositoryPort prices;

    @MockitoBean
    ShipmentNotifierPort shipmentNotifier;

    private String createActiveListing() {
        return createListing.create(new CreateListingCommand(
                "seller-x",
                "동시성 테스트 세트",
                "설명",
                100_000,
                ItemCondition.NEW_SEALED,
                com.gole.api.listing.domain.model.ConditionDisclosure.basic(),
                List.of("p.jpg"),
                "10307"));
    }

    @Test
    void concurrentPurchases_onlyOneWins() throws InterruptedException {
        String listingId = createActiveListing();

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger unavailable = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            final int n = i;
            pool.submit(() -> {
                try {
                    start.await();
                    placeOrder.place(new PlaceOrderCommand(listingId, "buyer-" + n));
                    success.incrementAndGet();
                } catch (ItemUnavailableException e) {
                    unavailable.incrementAndGet();
                } catch (Exception ignored) {
                    // 기타 동시성 예외도 비낙찰로 간주
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await(30, TimeUnit.SECONDS);
        pool.shutdownNow();

        // Property 1: 정확히 한 구매자만 주문을 만든다.
        assertThat(success.get()).isEqualTo(1);
        assertThat(getListing.getById(listingId).getStatus()).isEqualTo(ListingStatus.RESERVED);
    }

    @Test
    void completeIsExactlyOnce_andSettlesOnce() {
        String listingId = createActiveListing();
        String orderId = placeOrder.place(new PlaceOrderCommand(listingId, "buyer-1"));
        payOrder.pay(orderId);

        completeOrder.complete(orderId);
        // Property 2: 두 번째 완료는 거부되어 중복 정산이 발생하지 않는다.
        assertThatThrownBy(() -> completeOrder.complete(orderId)).isInstanceOf(OrderStateException.class);
    }

    @Test
    void paymentEvidenceCannotBeReusedAcrossSettlements() {
        String firstOrder = placeOrder.place(new PlaceOrderCommand(createActiveListing(), "buyer-1"));
        payOrder.pay(firstOrder);
        completeOrder.complete(firstOrder);
        String secondOrder = placeOrder.place(new PlaceOrderCommand(createActiveListing(), "buyer-2"));
        payOrder.pay(secondOrder);
        completeOrder.complete(secondOrder);

        settlements.claimManualPayout(firstOrder, "admin-1");
        settlements.markPaid(firstOrder, "admin-1", "BANK-UNIQUE-001");

        settlements.claimManualPayout(secondOrder, "admin-1");
        assertThatThrownBy(() -> settlements.markPaid(secondOrder, "admin-1", "BANK-UNIQUE-001"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("이미 다른 정산");
    }

    @Test
    void refundAndShipmentRegistration_cannotBothWin() throws Exception {
        for (int i = 0; i < 8; i++) {
            int attempt = i;
            String orderId = paidOrder("shipment-race-buyer-" + attempt);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Future<Boolean> refundWon = pool.submit(() -> runAfter(start, () -> refundOrder.refund(orderId)));
                Future<Boolean> shipmentWon = pool.submit(() -> runAfter(
                        start,
                        () -> registerWaybill.register(new RegisterWaybillCommand(
                                orderId, "seller-x", "cj_logistics", "12345678901" + attempt, "01012345678"))));

                start.countDown();
                boolean refunded = refundWon.get(30, TimeUnit.SECONDS);
                boolean shipped = shipmentWon.get(30, TimeUnit.SECONDS);

                assertThat(refunded).isNotEqualTo(shipped);
                OrderStatus finalStatus = getOrder.getById(orderId).getStatus();
                boolean shipmentExists = getShipment.getByOrderId(orderId).isPresent();
                assertThat((finalStatus == OrderStatus.REFUNDED && !shipmentExists)
                                || (finalStatus == OrderStatus.FUNDS_HELD && shipmentExists))
                        .isTrue();
            } finally {
                pool.shutdownNow();
            }
        }
    }

    @Test
    void shipmentFailure_rollsBackOrderFenceAndShipmentTogether() {
        String orderId = paidOrder("shipment-rollback-buyer");
        doThrow(new IllegalStateException("notification failure after shipment save"))
                .when(shipmentNotifier)
                .notifyWaybillRegistered(anyString(), anyString(), anyString(), anyString());

        try {
            assertThatThrownBy(() -> registerWaybill.register(new RegisterWaybillCommand(
                            orderId, "seller-x", "cj_logistics", "123456789012", "01012345678")))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            reset(shipmentNotifier);
        }

        assertThat(getOrder.getById(orderId).getShipmentRegisteredAt()).isNull();
        assertThat(getShipment.getByOrderId(orderId)).isEmpty();
    }

    @Test
    void disputedRefundAndCompletion_cannotBothCreateMoneyOutcomes() throws Exception {
        for (int i = 0; i < 8; i++) {
            String buyerId = "dispute-race-buyer-" + i;
            String orderId = paidOrder(buyerId);
            String listingId = getOrder.getById(orderId).getListingId();
            int priceCountBefore =
                    prices.findInRangeAscending("10307", null, null).size();
            openDispute.open(new OpenDisputeCommand(orderId, buyerId, "item_mismatch", "동시성 검증"));
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Future<Boolean> refundWon = pool.submit(() -> runAfter(start, () -> refundOrder.refund(orderId)));
                Future<Boolean> completionWon =
                        pool.submit(() -> runAfter(start, () -> completeOrder.complete(orderId)));

                start.countDown();
                boolean refunded = refundWon.get(30, TimeUnit.SECONDS);
                boolean completed = completionWon.get(30, TimeUnit.SECONDS);

                // 환불은 먼저 REFUND_PENDING을 선점한 뒤 PG를 호출하고 최종 확정한다. 그 사이
                // 오래된 구매확정 트랜잭션과 충돌하면 두 호출 모두 실패할 수 있지만, 그 상태는
                // 돈이 이중 귀속된 것이 아니라 재조정 가능한 안전한 대기 상태다.
                assertThat(refunded && completed).isFalse();
                OrderStatus finalStatus = getOrder.getById(orderId).getStatus();
                if (!refunded && !completed && finalStatus == OrderStatus.REFUND_PENDING) {
                    refundOrder.refund(orderId);
                    finalStatus = getOrder.getById(orderId).getStatus();
                }
                boolean hasSettlement = settlements.list(null, 100).stream()
                        .anyMatch(summary -> summary.orderId().equals(orderId));
                int priceCountAfter =
                        prices.findInRangeAscending("10307", null, null).size();
                if (finalStatus == OrderStatus.REFUNDED) {
                    assertThat(hasSettlement).isFalse();
                    assertThat(getListing.getById(listingId).getStatus()).isEqualTo(ListingStatus.ACTIVE);
                    assertThat(priceCountAfter).isEqualTo(priceCountBefore);
                } else {
                    assertThat(finalStatus).isEqualTo(OrderStatus.COMPLETED);
                    assertThat(hasSettlement).isTrue();
                    assertThat(getListing.getById(listingId).getStatus()).isEqualTo(ListingStatus.SOLD);
                    assertThat(priceCountAfter).isEqualTo(priceCountBefore + 1);
                }
            } finally {
                pool.shutdownNow();
            }
        }
    }

    private String paidOrder(String buyerId) {
        String orderId = placeOrder.place(new PlaceOrderCommand(createActiveListing(), buyerId));
        payOrder.pay(orderId);
        return orderId;
    }

    private static boolean runAfter(CountDownLatch start, Runnable action) {
        try {
            start.await();
            action.run();
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        } catch (RuntimeException expectedLoser) {
            return false;
        }
    }
}
