package com.gole.api.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.admin.application.port.out.AdminReadModelPort;
import com.gole.api.listing.application.port.in.CreateListingUseCase;
import com.gole.api.listing.application.port.in.CreateListingUseCase.CreateListingCommand;
import com.gole.api.listing.domain.model.ConditionDisclosure;
import com.gole.api.listing.domain.model.ItemCondition;
import com.gole.api.order.application.port.in.CompleteOrderUseCase;
import com.gole.api.order.application.port.in.GetOrderUseCase;
import com.gole.api.order.application.port.in.PayOrderUseCase;
import com.gole.api.order.application.port.in.PlaceOrderUseCase;
import com.gole.api.order.application.port.in.PlaceOrderUseCase.PlaceOrderCommand;
import com.gole.api.order.domain.model.Settlement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 정산 전표 영속화 통합 테스트(Testcontainers MongoDB).
 *
 * <p>단위 테스트는 인메모리 리포지토리를 쓰므로 <b>Mongo 왕복 매핑</b>과 <b>집계 경로</b>를
 * 검증하지 못한다. 실제로 정산이 유실됐던 원인이 그 두 곳이었다 — 영속성 어댑터가 정산을
 * null로 고정 저장했고, 운영 집계는 수수료를 아예 읽지 않았다.
 */
@SpringBootTest
@Testcontainers
class SettlementPersistenceIntegrationTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("gole.catalog.seed-on-empty", () -> "false");
        registry.add("gole.listing.seed-on-empty", () -> "false");
        registry.add("gole.pricing.seed-on-empty", () -> "false");
        registry.add("gole.community.seed-on-empty", () -> "false");
        registry.add("gole.report.seed-on-empty", () -> "false");
    }

    @Autowired
    CreateListingUseCase createListing;

    @Autowired
    PlaceOrderUseCase placeOrder;

    @Autowired
    PayOrderUseCase payOrder;

    @Autowired
    CompleteOrderUseCase completeOrder;

    @Autowired
    GetOrderUseCase getOrder;

    @Autowired
    AdminReadModelPort adminReadModel;

    private String createActiveListing(long price) {
        return createListing.create(new CreateListingCommand(
                "seller-settle",
                "정산 검증용 세트",
                "설명",
                price,
                ItemCondition.NEW_SEALED,
                ConditionDisclosure.basic(),
                List.of("p.jpg"),
                "10307"));
    }

    private String completedOrder(long price, String buyerId) {
        String listingId = createActiveListing(price);
        String orderId = placeOrder.place(new PlaceOrderCommand(listingId, buyerId));
        payOrder.pay(orderId);
        completeOrder.complete(orderId);
        return orderId;
    }

    @Test
    void settlement_survivesMongoRoundTrip() {
        String orderId = completedOrder(200_000, "buyer-rt");

        // getById는 저장소에서 다시 읽는다 — 메모리에 남은 객체가 아니라 문서에서 복원된 값이다.
        Settlement settlement = getOrder.getById(orderId).getSettlement();

        assertThat(settlement).isNotNull();
        assertThat(settlement.orderId()).isEqualTo(orderId);
        assertThat(settlement.sellerId()).isEqualTo("seller-settle");
        assertThat(settlement.grossAmount()).isEqualTo(200_000);
        assertThat(settlement.fee()).isEqualTo(10_000); // 기본 요율 5%
        assertThat(settlement.payout()).isEqualTo(190_000);
        assertThat(settlement.feeRate()).isEqualTo(0.05);
        assertThat(settlement.settledAt()).isNotNull();
    }

    /** 운영 대시보드의 "플랫폼 수익"은 완료 주문의 수수료 합이다. */
    @Test
    void adminOrderStats_sumsPlatformRevenue_fromSettlements() {
        long before = adminReadModel.orderStats().platformRevenue();

        completedOrder(100_000, "buyer-rev-1"); // 수수료 5,000
        completedOrder(300_000, "buyer-rev-2"); // 수수료 15,000

        assertThat(adminReadModel.orderStats().platformRevenue()).isEqualTo(before + 20_000);
    }

    /** 미정산 주문은 정산 값이 null이어야 한다 — "수수료 0원"과 구분되어야 하기 때문. */
    @Test
    void adminOrderRow_exposesSettlement_onlyForCompletedOrders() {
        String completed = completedOrder(400_000, "buyer-row");

        String pendingListing = createActiveListing(50_000);
        String pending = placeOrder.place(new PlaceOrderCommand(pendingListing, "buyer-pending"));

        List<AdminReadModelPort.OrderRow> rows = adminReadModel.recentOrders(null, 100);

        AdminReadModelPort.OrderRow completedRow =
                rows.stream().filter(r -> r.id().equals(completed)).findFirst().orElseThrow();
        assertThat(completedRow.fee()).isEqualTo(20_000);
        assertThat(completedRow.payout()).isEqualTo(380_000);
        assertThat(completedRow.feeRate()).isEqualTo(0.05);

        AdminReadModelPort.OrderRow pendingRow =
                rows.stream().filter(r -> r.id().equals(pending)).findFirst().orElseThrow();
        assertThat(pendingRow.fee()).isNull();
        assertThat(pendingRow.payout()).isNull();
        assertThat(pendingRow.feeRate()).isNull();
    }
}
