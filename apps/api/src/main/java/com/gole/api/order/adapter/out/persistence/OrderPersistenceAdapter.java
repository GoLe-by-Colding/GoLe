package com.gole.api.order.adapter.out.persistence;

import com.gole.api.order.adapter.out.persistence.OrderDocument.PaymentMethodDocument;
import com.gole.api.order.adapter.out.persistence.OrderDocument.SettlementDocument;
import com.gole.api.order.adapter.out.persistence.OrderDocument.StatusChangeDocument;
import com.gole.api.order.application.port.out.OrderRepositoryPort;
import com.gole.api.order.domain.model.Order;
import com.gole.api.order.domain.model.OrderStatus;
import com.gole.api.order.domain.model.OrderStatusChange;
import com.gole.api.order.domain.model.PaymentMethod;
import com.gole.api.order.domain.model.PaymentMethodType;
import com.gole.api.order.domain.model.Settlement;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 주문 영속성 어댑터. 도메인 {@link Order}와 {@link OrderDocument}를 양방향 매핑한다.
 *
 * <p>낙관적 락 버전은 {@code OrderDocument.@Version}을 통해 왕복하며, 도메인의
 * {@code version} 필드와 동기화된다.
 */
@Component
public class OrderPersistenceAdapter implements OrderRepositoryPort {

    private final OrderMongoRepository repository;

    public OrderPersistenceAdapter(OrderMongoRepository repository) {
        this.repository = repository;
    }

    @Override
    public Order save(Order order) {
        return toDomain(repository.save(toDocument(order)));
    }

    @Override
    public Optional<Order> findById(String orderId) {
        return repository.findById(orderId).map(this::toDomain);
    }

    /**
     * 발급 이력에서 먼저 찾고, 없으면 주문 id로 되짚는다.
     *
     * <p>후자는 결제 시도 도입 이전 주문을 위한 것이다. 그 시절 결제 식별자는 주문 id 자체였고
     * 그렇게 결제된 주문의 웹훅이 아직 도착할 수 있다.
     */
    @Override
    public Optional<Order> findByPaymentId(String paymentId) {
        return repository
                .findFirstByPaymentIdsContains(paymentId)
                .or(() -> repository.findById(paymentId))
                .map(this::toDomain);
    }

    @Override
    public List<Order> findByBuyerId(String buyerId) {
        return repository.findByBuyerId(buyerId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Order> findBySellerId(String sellerId) {
        return repository.findBySellerId(sellerId).stream().map(this::toDomain).toList();
    }

    private OrderDocument toDocument(Order order) {
        List<StatusChangeDocument> history = order.getHistory().stream()
                .map(change -> StatusChangeDocument.of(change.status(), change.occurredAt()))
                .toList();
        return new OrderDocument(
                order.getId(),
                order.getListingId(),
                order.getBuyerId(),
                order.getSellerId(),
                order.getCatalogSetNumber(),
                order.getListingCondition(),
                order.getAmount(),
                order.getStatus().name(),
                order.getCreatedAt(),
                history,
                toPaymentMethodDocument(order.getPaymentMethod()),
                toSettlementDocument(order.getSettlement()),
                order.getPaymentAttempt(),
                order.getIssuedPaymentIds(),
                order.getVersion());
    }

    private Order toDomain(OrderDocument document) {
        List<OrderStatusChange> history = document.getStatusHistory().stream()
                .map(change -> new OrderStatusChange(OrderStatus.valueOf(change.getStatus()), change.getOccurredAt()))
                .toList();
        return new Order(
                document.getId(),
                document.getListingId(),
                document.getBuyerId(),
                document.getSellerId(),
                document.getCatalogSetNumber(),
                document.getListingCondition(),
                document.getAmount(),
                OrderStatus.valueOf(document.getStatus()),
                document.getCreatedAt(),
                history,
                toPaymentMethod(document.getPaymentMethod()),
                toSettlement(document.getSettlement()),
                document.getPaymentAttempt(),
                document.getVersion());
    }

    private static PaymentMethodDocument toPaymentMethodDocument(PaymentMethod method) {
        if (method == null) {
            return null;
        }
        return new PaymentMethodDocument(method.type().name(), method.provider());
    }

    /**
     * 문서 → 도메인. 모르는 분류 문자열은 예외 대신 {@link PaymentMethodType#UNKNOWN}으로 접는다.
     *
     * <p>{@code valueOf}가 던지면 주문 전체를 읽지 못하게 된다. 결제수단 하나를 모른다고
     * 주문 조회·정산 조회가 함께 무너지는 것은 손해가 너무 크다(신버전이 쓴 값을 구버전이
     * 읽는 롤백 상황이 대표적이다).
     */
    private static PaymentMethod toPaymentMethod(PaymentMethodDocument document) {
        if (document == null) {
            return null;
        }
        PaymentMethodType type;
        try {
            type = PaymentMethodType.valueOf(document.getType());
        } catch (IllegalArgumentException | NullPointerException ex) {
            type = PaymentMethodType.UNKNOWN;
        }
        return new PaymentMethod(type, document.getProvider());
    }

    private static SettlementDocument toSettlementDocument(Settlement settlement) {
        if (settlement == null) {
            return null;
        }
        return new SettlementDocument(
                settlement.orderId(),
                settlement.sellerId(),
                settlement.grossAmount(),
                settlement.fee(),
                settlement.payout(),
                settlement.feeRate(),
                settlement.settledAt());
    }

    private static Settlement toSettlement(SettlementDocument document) {
        if (document == null) {
            return null;
        }
        return new Settlement(
                document.getOrderId(),
                document.getSellerId(),
                document.getGrossAmount(),
                document.getFee(),
                document.getPayout(),
                document.getFeeRate(), // 레거시 문서는 getter가 당시 상수로 보정한다
                document.getSettledAt());
    }
}
