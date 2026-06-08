package com.gole.api.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.listing.application.port.in.CreateListingUseCase;
import com.gole.api.listing.application.port.in.CreateListingUseCase.CreateListingCommand;
import com.gole.api.listing.application.port.in.GetListingUseCase;
import com.gole.api.listing.domain.model.ItemCondition;
import com.gole.api.listing.domain.model.ListingStatus;
import com.gole.api.order.application.port.in.CompleteOrderUseCase;
import com.gole.api.order.application.port.in.PayOrderUseCase;
import com.gole.api.order.application.port.in.PlaceOrderUseCase;
import com.gole.api.order.application.port.in.PlaceOrderUseCase.PlaceOrderCommand;
import com.gole.api.order.domain.exception.ItemUnavailableException;
import com.gole.api.order.domain.exception.OrderStateException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
    }

    @Autowired CreateListingUseCase createListing;
    @Autowired GetListingUseCase getListing;
    @Autowired PlaceOrderUseCase placeOrder;
    @Autowired PayOrderUseCase payOrder;
    @Autowired CompleteOrderUseCase completeOrder;

    private String createActiveListing() {
        return createListing.create(new CreateListingCommand(
                "seller-x", "동시성 테스트 세트", "설명", 100_000,
                ItemCondition.NEW_SEALED, com.gole.api.listing.domain.model.ConditionDisclosure.basic(),
                List.of("p.jpg"), "10307"));
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
        assertThatThrownBy(() -> completeOrder.complete(orderId))
                .isInstanceOf(OrderStateException.class);
    }
}
