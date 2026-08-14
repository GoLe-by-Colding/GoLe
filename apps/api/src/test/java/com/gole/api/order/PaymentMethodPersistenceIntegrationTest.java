package com.gole.api.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.admin.application.port.out.AdminReadModelPort;
import com.gole.api.listing.application.port.in.CreateListingUseCase;
import com.gole.api.listing.application.port.in.CreateListingUseCase.CreateListingCommand;
import com.gole.api.listing.domain.model.ConditionDisclosure;
import com.gole.api.listing.domain.model.ItemCondition;
import com.gole.api.order.application.port.in.GetOrderUseCase;
import com.gole.api.order.application.port.in.PayOrderUseCase;
import com.gole.api.order.application.port.in.PlaceOrderUseCase;
import com.gole.api.order.application.port.in.PlaceOrderUseCase.PlaceOrderCommand;
import com.gole.api.order.domain.model.PaymentMethod;
import com.gole.api.order.domain.model.PaymentMethodType;
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
 * 결제수단 영속화 통합 테스트(Testcontainers MongoDB).
 *
 * <p>단위 테스트는 인메모리 리포지토리를 쓰므로 <b>Mongo 왕복 매핑</b>을 검증하지 못한다.
 * 정산 전표가 실제로 유실됐던 원인이 바로 그 구간이었다(영속성 어댑터가 값을 버렸다).
 * 결제수단도 같은 경로를 지나므로 같은 방식으로 못 박아 둔다.
 *
 * <p>기본 프로파일은 스텁 PG({@code portone.enabled=false})라 결제수단이 CARD로 결정된다.
 */
@SpringBootTest
@Testcontainers
class PaymentMethodPersistenceIntegrationTest {

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
    GetOrderUseCase getOrder;

    @Autowired
    AdminReadModelPort adminReadModel;

    private String placedOrder(long price, String buyerId) {
        String listingId = createListing.create(new CreateListingCommand(
                "seller-pay",
                "결제수단 검증용 세트",
                "설명",
                price,
                ItemCondition.NEW_SEALED,
                ConditionDisclosure.basic(),
                List.of("p.jpg"),
                "10307"));
        return placeOrder.place(new PlaceOrderCommand(listingId, buyerId));
    }

    @Test
    void paymentMethod_survivesMongoRoundTrip() {
        String orderId = placedOrder(150_000, "buyer-pm");
        payOrder.pay(orderId);

        // getById는 메모리에 남은 객체가 아니라 문서에서 복원된 값을 돌려준다.
        PaymentMethod method = getOrder.getById(orderId).getPaymentMethod();

        assertThat(method).isNotNull();
        assertThat(method.type()).isEqualTo(PaymentMethodType.CARD);
        assertThat(method.provider()).isNull();
    }

    /** 결제 전에는 값이 없어야 한다 — "미결제"와 "확인 불가(UNKNOWN)"는 다른 뜻이다. */
    @Test
    void paymentMethod_isAbsent_beforePayment() {
        String orderId = placedOrder(70_000, "buyer-pm-pending");

        assertThat(getOrder.getById(orderId).getPaymentMethod()).isNull();
    }

    /** 운영 목록에도 같은 값이 실려야 한다(집계 경로는 도메인 매핑과 별개다). */
    @Test
    void adminOrderRow_exposesPaymentMethod_onlyAfterPayment() {
        String paid = placedOrder(210_000, "buyer-pm-row");
        payOrder.pay(paid);
        String pending = placedOrder(60_000, "buyer-pm-row-2");

        List<AdminReadModelPort.OrderRow> rows = adminReadModel.recentOrders(null, 100);

        AdminReadModelPort.OrderRow paidRow =
                rows.stream().filter(r -> r.id().equals(paid)).findFirst().orElseThrow();
        assertThat(paidRow.paymentMethod()).isNotNull();
        assertThat(paidRow.paymentMethod().type()).isEqualTo("CARD");
        assertThat(paidRow.paymentMethod().provider()).isNull();

        AdminReadModelPort.OrderRow pendingRow =
                rows.stream().filter(r -> r.id().equals(pending)).findFirst().orElseThrow();
        assertThat(pendingRow.paymentMethod()).isNull();
    }
}
