package com.gole.api.order.adapter.out.persistence;

import com.gole.api.order.adapter.out.persistence.OrderDocument.PaymentMethodDocument;
import com.gole.api.order.adapter.out.persistence.OrderDocument.StatusChangeDocument;
import com.gole.api.order.application.port.out.OrderRepositoryPort;
import com.gole.api.order.domain.model.Order;
import com.gole.api.order.domain.model.OrderStatus;
import com.gole.api.order.domain.model.OrderStatusChange;
import com.gole.api.order.domain.model.PaymentMethod;
import com.gole.api.order.domain.model.PaymentMethodType;
import java.time.Instant;
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

    @Override
    public List<Order> findByBuyerId(String buyerId) {
        return repository.findTop100ByBuyerIdOrderByCreatedAtDesc(buyerId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Order> findBySellerId(String sellerId) {
        return repository.findTop100BySellerIdOrderByCreatedAtDesc(sellerId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Order> findPaymentPendingCreatedBefore(Instant cutoff) {
        return repository
                .findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(OrderStatus.PAYMENT_PENDING.name(), cutoff)
                .stream()
                .map(this::toDomain)
                .toList();
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
                null,
                toPaymentMethodDocument(order.getPaymentMethod()),
                order.getVersion());
    }

    private static PaymentMethodDocument toPaymentMethodDocument(PaymentMethod method) {
        return method == null ? null : new PaymentMethodDocument(method.type().name(), method.provider());
    }

    /**
     * 저장된 결제수단을 도메인으로 되돌린다.
     *
     * <p>열거형에 없는 {@code type}은 예외 대신 {@link PaymentMethodType#UNKNOWN}으로 읽는다.
     * 분류 하나를 몰라서 주문 조회 전체가 실패하는 편이 훨씬 나쁘다.
     */
    private static PaymentMethod toPaymentMethod(PaymentMethodDocument document) {
        if (document == null) {
            return null;
        }
        return new PaymentMethod(parseType(document.getType()), document.getProvider());
    }

    private static PaymentMethodType parseType(String raw) {
        if (raw == null) {
            return PaymentMethodType.UNKNOWN;
        }
        try {
            return PaymentMethodType.valueOf(raw);
        } catch (IllegalArgumentException unknownType) {
            return PaymentMethodType.UNKNOWN;
        }
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
                toPaymentMethod(document.getPaymentMethod()),
                document.getCreatedAt(),
                history,
                document.getVersion());
    }
}
