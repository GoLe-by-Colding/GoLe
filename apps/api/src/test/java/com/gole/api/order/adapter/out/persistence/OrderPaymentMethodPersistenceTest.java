package com.gole.api.order.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gole.api.order.adapter.out.persistence.OrderDocument.PaymentMethodDocument;
import com.gole.api.order.adapter.out.persistence.OrderDocument.StatusChangeDocument;
import com.gole.api.order.domain.model.Order;
import com.gole.api.order.domain.model.OrderStatus;
import com.gole.api.order.domain.model.PaymentMethod;
import com.gole.api.order.domain.model.PaymentMethodType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 결제수단의 저장·복원 매핑.
 *
 * <p>어댑터가 한쪽 방향만 매핑하는 실수는 컴파일로 잡히지 않는다. 저장은 되는데 읽을 때 빠지면
 * 결제수단이 조용히 사라지고, 사라진 사실은 PG 원장에서 되찾아야 한다.
 */
class OrderPaymentMethodPersistenceTest {

    private static final Instant NOW = Instant.parse("2026-08-17T05:00:00Z");

    /** 저장된 문서를 그대로 돌려주는 저장소. save()의 왕복이 곧 양방향 매핑 검증이 된다. */
    private static OrderPersistenceAdapter adapterEchoingSavedDocument() {
        OrderMongoRepository repository = mock(OrderMongoRepository.class);
        when(repository.save(any(OrderDocument.class))).thenAnswer(call -> call.getArgument(0));
        return new OrderPersistenceAdapter(repository);
    }

    @Test
    @DisplayName("결제수단이 저장 후 복원까지 살아남는다")
    void roundTripsPaymentMethod() {
        Order order = Order.place("order-1", "listing-1", "buyer-1", "seller-1", "10305", "new_sealed", 15_000, NOW);
        order.confirmFundsHeld(NOW, new PaymentMethod(PaymentMethodType.EASY_PAY, "KAKAOPAY"));

        Order restored = adapterEchoingSavedDocument().save(order);

        assertThat(restored.getPaymentMethod()).isEqualTo(new PaymentMethod(PaymentMethodType.EASY_PAY, "KAKAOPAY"));
    }

    @Test
    @DisplayName("결제 전 주문은 결제수단 없이 저장된다")
    void savesNoPaymentMethodBeforePayment() {
        Order order = Order.place("order-1", "listing-1", "buyer-1", "seller-1", "10305", "new_sealed", 15_000, NOW);

        Order restored = adapterEchoingSavedDocument().save(order);

        assertThat(restored.getPaymentMethod()).isNull();
    }

    /**
     * 결제수단 도입 이전에 저장된 주문은 필드가 아예 없다. 조회가 깨지지 않고 "모른다"로 읽혀야 한다.
     */
    @Test
    @DisplayName("결제수단 필드가 없는 레거시 문서도 그대로 읽힌다")
    void readsLegacyDocumentWithoutPaymentMethod() {
        OrderMongoRepository repository = mock(OrderMongoRepository.class);
        when(repository.findById("order-legacy")).thenReturn(Optional.of(documentWithPaymentMethod(null)));

        Optional<Order> restored = new OrderPersistenceAdapter(repository).findById("order-legacy");

        assertThat(restored).isPresent();
        assertThat(restored.get().getPaymentMethod()).isNull();
    }

    /**
     * 우리가 모르는 {@code type}을 만나도 주문 조회는 성공해야 한다. 분류 하나를 몰라서
     * 주문 상세 전체가 500이 되는 편이 훨씬 나쁘다.
     */
    @Test
    @DisplayName("알 수 없는 결제수단 분류는 UNKNOWN으로 읽고 주문 조회를 깨뜨리지 않는다")
    void foldsUnknownStoredTypeToUnknown() {
        OrderMongoRepository repository = mock(OrderMongoRepository.class);
        when(repository.findById("order-teleport"))
                .thenReturn(Optional.of(documentWithPaymentMethod(new PaymentMethodDocument("TELEPORT", "KAKAOPAY"))));

        Order restored = new OrderPersistenceAdapter(repository)
                .findById("order-teleport")
                .orElseThrow();

        assertThat(restored.getPaymentMethod().type()).isEqualTo(PaymentMethodType.UNKNOWN);
        assertThat(restored.getPaymentMethod().provider()).isEqualTo("KAKAOPAY");
    }

    private static OrderDocument documentWithPaymentMethod(PaymentMethodDocument paymentMethod) {
        return new OrderDocument(
                "order-1",
                "listing-1",
                "buyer-1",
                "seller-1",
                "10305",
                "new_sealed",
                15_000,
                OrderStatus.FUNDS_HELD.name(),
                NOW,
                NOW,
                null,
                null,
                null,
                null,
                List.of(new StatusChangeDocument(OrderStatus.FUNDS_HELD.name(), NOW)),
                null,
                paymentMethod,
                1L);
    }
}
