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
 * <p>단위 테스트는 리포지토리를 목으로 대체하므로 <b>실제 Mongo 왕복</b>을 검증하지 못한다.
 * 결제수단은 임베디드 문서로 저장되는데, 임베디드 매핑이 한쪽 방향만 동작하면 값이 조용히
 * 사라지고 그 사실은 PG 원장에서 되찾아야 한다. 그 구간을 여기서 못 박는다.
 *
 * <p>기본 프로파일은 스텁 PG({@code portone.enabled=false})이고, 스텁은 운영에서 허용하는 것과
 * 같은 결제수단(카카오페이 간편결제)을 보고한다.
 */
@SpringBootTest
@Testcontainers
class PaymentMethodPersistenceIntegrationTest {

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

    /** 자기거래는 금지되므로 판매자와 구매자를 반드시 다르게 둔다. */
    private String placedOrder(long price, String buyerId) {
        String listingId = createListing.create(new CreateListingCommand(
                "seller-pm",
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
        assertThat(method.type()).isEqualTo(PaymentMethodType.EASY_PAY);
        assertThat(method.provider()).isEqualTo("KAKAOPAY");
    }

    /** 결제 전에는 값이 없어야 한다 — "미결제"와 "확인 불가(UNKNOWN)"는 다른 뜻이다. */
    @Test
    void paymentMethod_isAbsent_beforePayment() {
        String orderId = placedOrder(70_000, "buyer-pm-pending");

        assertThat(getOrder.getById(orderId).getPaymentMethod()).isNull();
    }

    /** 운영 목록에도 같은 값이 실려야 한다 — 읽기 모델은 컬렉션을 직접 집계하므로 도메인 매핑과 별개다. */
    @Test
    void adminOrderRow_exposesPaymentMethod_onlyAfterPayment() {
        String paid = placedOrder(210_000, "buyer-pm-row");
        payOrder.pay(paid);
        String pending = placedOrder(60_000, "buyer-pm-row-2");

        List<AdminReadModelPort.OrderRow> rows = adminReadModel.recentOrders(null, null, 100);

        AdminReadModelPort.OrderRow paidRow =
                rows.stream().filter(r -> r.id().equals(paid)).findFirst().orElseThrow();
        assertThat(paidRow.paymentMethod()).isNotNull();
        assertThat(paidRow.paymentMethod().type()).isEqualTo("EASY_PAY");
        assertThat(paidRow.paymentMethod().provider()).isEqualTo("KAKAOPAY");

        AdminReadModelPort.OrderRow pendingRow =
                rows.stream().filter(r -> r.id().equals(pending)).findFirst().orElseThrow();
        assertThat(pendingRow.paymentMethod()).isNull();
    }
}
